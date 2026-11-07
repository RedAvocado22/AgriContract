package com.agricontract.product.infrastructure.client;

import com.agricontract.product.application.dto.ReputationLookup;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads seller reputation for one search page with a single
 * {@code POST /internal/v1/reputation/public-summaries} call (golden-flow-api.yaml getPublicReputationSummaries).
 * The call is service-to-service ({@code X-Internal-Secret}); it never carries the gateway secret or the viewer.
 * Only {@code score} and {@code locked} are kept. Any failure leaves the affected sellers unknown; nothing is thrown.
 */
@Component
public class ReputationSummaryClient implements SellerReputationPort {
    private static final Logger LOG = LoggerFactory.getLogger(ReputationSummaryClient.class);
    private static final String PATH = "/internal/v1/reputation/public-summaries";
    private static final MediaType JSON_SUFFIX = new MediaType("application", "*+json");
    private static final BigDecimal MAX_SCORE = BigDecimal.valueOf(100);
    /** ReputationBatchSummaryRequest.userIds maxItems; a search page is capped at 100 listings anyway. */
    static final int MAX_BATCH = 100;

    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient client;

    public ReputationSummaryClient(RestClient.Builder builder,
                                   @Value("${reputation-service.url}") String baseUrl,
                                   @Value("${service.internal-secret}") String internalSecret,
                                   @Value("${reputation-service.connect-timeout-ms}") int connectTimeoutMs,
                                   @Value("${reputation-service.read-timeout-ms}") int readTimeoutMs) {
        if (internalSecret == null || internalSecret.isBlank()) {
            throw new IllegalArgumentException("SERVICE_INTERNAL_SECRET must be configured");
        }
        HttpClient transport = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(transport);
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        client = builder.requestFactory(factory).baseUrl(baseUrl)
                .defaultHeader("X-Internal-Secret", internalSecret)
                .build();
    }

    @Override
    public ReputationLookup lookup(Set<String> sellerIds) {
        if (sellerIds.isEmpty()) {
            return new ReputationLookup(Map.of(), true);
        }
        if (sellerIds.size() > MAX_BATCH) {
            return incomplete(Map.of(), sellerIds.size(), "batch_too_large");
        }
        Map<String, SellerReputation> found;
        try {
            found = parse(sellerIds, read(sellerIds));
        } catch (InvalidAnswer invalid) {
            return incomplete(Map.of(), sellerIds.size(), invalid.category);
        } catch (RuntimeException transport) {
            return incomplete(Map.of(), sellerIds.size(), "transport");
        }
        if (found.size() < sellerIds.size()) {
            return incomplete(found, sellerIds.size(), "missing_items");
        }
        return new ReputationLookup(found, true);
    }

    private JsonNode read(Set<String> sellerIds) {
        return client.post().uri(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("userIds", List.copyOf(sellerIds)))
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
    }

    /*
     * ReputationBatchSummaryResponse: {items: [{userId, score: null | 0..100 integer, locked: boolean}]}, exactly one
     * item per requested id, order not frozen. An item for an id we did not ask for, a repeated id or a bad field
     * makes the whole answer untrusted; an id with no item is just left unknown.
     */
    private static Map<String, SellerReputation> parse(Set<String> sellerIds, JsonNode body) {
        if (body == null || !body.isObject() || body.get("items") == null || !body.get("items").isArray()) {
            throw new InvalidAnswer("malformed_response");
        }
        Map<String, String> requested = new HashMap<>();
        sellerIds.forEach(id -> requested.put(id.toLowerCase(), id));
        Map<String, SellerReputation> found = new HashMap<>();
        for (JsonNode item : body.get("items")) {
            if (!item.isObject() || item.get("userId") == null || !item.get("userId").isTextual()) {
                throw new InvalidAnswer("malformed_response");
            }
            String sellerId = requested.get(item.get("userId").textValue().toLowerCase());
            if (sellerId == null || found.containsKey(sellerId)) {
                throw new InvalidAnswer("identity_mismatch");
            }
            found.put(sellerId, reputation(item));
        }
        return found;
    }

    private static SellerReputation reputation(JsonNode item) {
        JsonNode score = item.get("score");
        JsonNode locked = item.get("locked");
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

    private static ReputationLookup incomplete(Map<String, SellerReputation> found, int requested, String category) {
        // Bounded category and counts only: never the seller ids nor the remote body.
        LOG.warn("reputation_dependency_failure unread={} of={} category={}",
                requested - found.size(), requested, category);
        return new ReputationLookup(found, false);
    }

    private static final class InvalidAnswer extends RuntimeException {
        private final String category;

        private InvalidAnswer(String category) {
            super(category, null, false, false);
            this.category = category;
        }
    }
}
