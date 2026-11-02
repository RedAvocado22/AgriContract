package com.agricontract.product.infrastructure.client;

import com.agricontract.product.application.dto.ReputationLookup;
import com.agricontract.product.application.dto.ReputationViewer;
import com.agricontract.product.application.dto.SellerReputation;
import com.agricontract.product.application.port.SellerReputationPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Reads {@code GET /api/v1/reputation/{userId}/public-summary} for every seller on a search page, in parallel and
 * within one time budget. The endpoint only accepts a gateway-authenticated user, so each read carries the gateway
 * secret and the identity of the viewer who searched (the same identity the gateway gave this request).
 * Only {@code score} and {@code locked} are kept. Any failure leaves that seller unknown; nothing is thrown.
 */
@Component
public class ReputationSummaryClient implements SellerReputationPort {
    private static final Logger LOG = LoggerFactory.getLogger(ReputationSummaryClient.class);
    private static final MediaType JSON_SUFFIX = new MediaType("application", "*+json");
    private static final BigDecimal MAX_SCORE = BigDecimal.valueOf(100);

    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient client;
    private final long budgetNanos;

    public ReputationSummaryClient(RestClient.Builder builder,
                                   @Value("${reputation-service.url}") String baseUrl,
                                   @Value("${gateway.internal-secret}") String gatewaySecret,
                                   @Value("${reputation-service.connect-timeout-ms}") int connectTimeoutMs,
                                   @Value("${reputation-service.read-timeout-ms}") int readTimeoutMs,
                                   @Value("${reputation-service.lookup-budget-ms}") int budgetMs) {
        if (gatewaySecret == null || gatewaySecret.isBlank()) {
            throw new IllegalArgumentException("GATEWAY_INTERNAL_SECRET must be configured");
        }
        HttpClient transport = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(transport);
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        client = builder.requestFactory(factory).baseUrl(baseUrl)
                .defaultHeader("X-Gateway-Secret", gatewaySecret)
                .build();
        budgetNanos = TimeUnit.MILLISECONDS.toNanos(budgetMs);
    }

    @Override
    public ReputationLookup lookup(Set<String> sellerIds, ReputationViewer viewer) {
        if (sellerIds.isEmpty()) {
            return new ReputationLookup(Map.of(), true);
        }
        long deadline = System.nanoTime() + budgetNanos;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        Map<String, Future<SellerReputation>> reads = new LinkedHashMap<>();
        Map<String, SellerReputation> found = new HashMap<>();
        Map<String, Integer> failures = new TreeMap<>();
        try {
            sellerIds.forEach(sellerId -> reads.put(sellerId, executor.submit(() -> read(sellerId, viewer))));
            for (var read : reads.entrySet()) {
                try {
                    long remaining = Math.max(0, deadline - System.nanoTime());
                    found.put(read.getKey(), read.getValue().get(remaining, TimeUnit.NANOSECONDS));
                } catch (TimeoutException late) {
                    failures.merge("budget_exceeded", 1, Integer::sum);
                } catch (ExecutionException failed) {
                    String category = failed.getCause() instanceof InvalidAnswer invalid
                            ? invalid.category : "transport";
                    failures.merge(category, 1, Integer::sum);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    failures.merge("interrupted", 1, Integer::sum);
                    break;
                }
            }
        } finally {
            // Never wait for reads past the budget: interrupt them and answer with what arrived in time.
            executor.shutdownNow();
        }
        boolean complete = found.size() == sellerIds.size();
        if (!complete) {
            // Bounded categories and counts only: never the seller or viewer ids, nor the remote body.
            LOG.warn("reputation_dependency_failure unread={} of={} categories={}",
                    sellerIds.size() - found.size(), sellerIds.size(), failures);
        }
        return new ReputationLookup(found, complete);
    }

    private SellerReputation read(String sellerId, ReputationViewer viewer) {
        JsonNode body = client.get().uri("/api/v1/reputation/{userId}/public-summary", sellerId)
                .header("X-User-Id", viewer.userId())
                .header("X-User-Role", String.join(",", viewer.roles()))
                .exchange((request, response) -> {
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw new InvalidAnswer("dependency_http");
                    }
                    try {
                        MediaType contentType = response.getHeaders().getContentType();
                        if (contentType == null || !(MediaType.APPLICATION_JSON.isCompatibleWith(contentType)
                                || JSON_SUFFIX.isCompatibleWith(contentType))) {
                            throw new InvalidAnswer("malformed_response");
                        }
                        return mapper.readTree(response.getBody());
                    } catch (JsonProcessingException | InvalidMediaTypeException malformed) {
                        throw new InvalidAnswer("malformed_response");
                    }
                });
        return parse(sellerId, body);
    }

    // ReputationPublicSummary (golden-flow-api.yaml): userId echoes the path, score is null or 0..100, locked boolean.
    private static SellerReputation parse(String sellerId, JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new InvalidAnswer("malformed_response");
        }
        JsonNode userId = body.get("userId");
        if (userId == null || !userId.isTextual() || !sellerId.equalsIgnoreCase(userId.textValue())) {
            throw new InvalidAnswer("identity_mismatch");
        }
        JsonNode score = body.get("score");
        JsonNode locked = body.get("locked");
        if (score == null || locked == null || !locked.isBoolean()) {
            throw new InvalidAnswer("malformed_response");
        }
        if (score.isNull()) {
            return new SellerReputation(null, locked.booleanValue());
        }
        if (!score.isNumber() || !score.canConvertToExactIntegral()
                || score.decimalValue().signum() < 0 || score.decimalValue().compareTo(MAX_SCORE) > 0) {
            throw new InvalidAnswer("malformed_response");
        }
        return new SellerReputation(score.intValue(), locked.booleanValue());
    }

    private static final class InvalidAnswer extends RuntimeException {
        private final String category;

        private InvalidAnswer(String category) {
            super(category, null, false, false);
            this.category = category;
        }
    }
}
