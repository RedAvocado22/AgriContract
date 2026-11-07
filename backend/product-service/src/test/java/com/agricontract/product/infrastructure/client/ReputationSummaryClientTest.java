package com.agricontract.product.infrastructure.client;

import com.agricontract.product.application.dto.ReputationLookup;
import com.agricontract.product.application.dto.SellerReputation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boundary of the reputation-service internal batch read (golden-flow-api.yaml getPublicReputationSummaries) against
 * a real HTTP stub: one POST per page, service auth only, fields kept, and every failure degrading to "unknown".
 */
class ReputationSummaryClientTest {
    private static final String SELLER_A = "9b0c8f0e-2f43-4a51-9d8c-1d6f3f1e7a10";
    private static final String SELLER_B = "3f2a1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d";
    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpServer server;
    private ReputationSummaryClient client;
    private final List<Map<String, String>> calls = new CopyOnWriteArrayList<>();
    private volatile String responseBody;
    private volatile int status = 200;
    private volatile long delayMs;
    private volatile String contentType = "application/json";

    private static String item(String userId, String score, String locked) {
        return "{\"userId\":\"%s\",\"score\":%s,\"locked\":%s}".formatted(userId, score, locked);
    }

    private static String items(String... items) {
        return "{\"items\":[" + String.join(",", items) + "]}";
    }

    @BeforeEach
    void startReputationStub() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", exchange -> {
            calls.add(Map.of(
                    "method", exchange.getRequestMethod(),
                    "path", exchange.getRequestURI().getPath(),
                    "internal", String.valueOf(exchange.getRequestHeaders().getFirst("X-Internal-Secret")),
                    "gateway", String.valueOf(exchange.getRequestHeaders().getFirst("X-Gateway-Secret")),
                    "user", String.valueOf(exchange.getRequestHeaders().getFirst("X-User-Id")),
                    "contentType", String.valueOf(exchange.getRequestHeaders().getFirst("Content-Type")),
                    "body", new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = (responseBody == null ? items() : responseBody).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        client = client(url(), 400);
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static ReputationSummaryClient client(String url, int readTimeoutMs) {
        return new ReputationSummaryClient(RestClient.builder(), url, "svc-secret", 300, readTimeoutMs);
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    private static List<String> requestedIds(Map<String, String> call) throws Exception {
        JsonNode body = JSON.readTree(call.get("body"));
        List<String> ids = new ArrayList<>();
        body.get("userIds").forEach(id -> ids.add(id.textValue()));
        return ids;
    }

    @Test
    void readsTheWholePageInOneServiceCall_andKeepsOnlyScoreAndLocked() throws Exception {
        responseBody = items(item(SELLER_B, "null", "false"), item(SELLER_A, "87", "true"));

        ReputationLookup lookup = client.lookup(new LinkedHashSet<>(List.of(SELLER_A, SELLER_B)));

        assertThat(lookup.complete()).isTrue();
        assertThat(lookup.bySeller()).containsExactlyInAnyOrderEntriesOf(Map.of(
                SELLER_A, new SellerReputation(87, true),
                SELLER_B, new SellerReputation(null, false)));
        assertThat(calls).hasSize(1);
        Map<String, String> call = calls.get(0);
        assertThat(call).containsAllEntriesOf(Map.of(
                "method", "POST",
                "path", "/internal/v1/reputation/public-summaries",
                "internal", "svc-secret",
                "gateway", "null",
                "user", "null"));
        assertThat(call.get("contentType")).startsWith("application/json");
        assertThat(requestedIds(call)).containsExactlyInAnyOrder(SELLER_A, SELLER_B);
    }

    @Test
    void integralDecimalScore_andUpperCaseIdEcho_areAccepted() {
        responseBody = items(item(SELLER_A.toUpperCase(), "75.0", "false"));

        ReputationLookup lookup = client.lookup(Set.of(SELLER_A));

        assertThat(lookup.complete()).isTrue();
        assertThat(lookup.bySeller()).containsExactlyEntriesOf(Map.of(SELLER_A, new SellerReputation(75, false)));
    }

    @Test
    void sellerMissingFromTheAnswer_isOnlyThatSellerUnknown() {
        responseBody = items(item(SELLER_A, "60", "false"));

        ReputationLookup lookup = client.lookup(Set.of(SELLER_A, SELLER_B));

        assertThat(lookup.complete()).isFalse();
        assertThat(lookup.bySeller()).containsOnlyKeys(SELLER_A);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"items\":[{\"userId\":\"%s\",\"score\":101,\"locked\":false}]}",
            "{\"items\":[{\"userId\":\"%s\",\"score\":-1,\"locked\":false}]}",
            "{\"items\":[{\"userId\":\"%s\",\"score\":80.5,\"locked\":false}]}",
            "{\"items\":[{\"userId\":\"%s\",\"score\":\"80\",\"locked\":false}]}",
            "{\"items\":[{\"userId\":\"%s\",\"locked\":false}]}",
            "{\"items\":[{\"userId\":\"%s\",\"score\":80}]}",
            "{\"items\":[{\"userId\":\"%s\",\"score\":80,\"locked\":\"no\"}]}",
            "{\"items\":[{\"score\":80,\"locked\":false}]}",
            "{\"items\":[{\"userId\":\"someone-else\",\"score\":80,\"locked\":false}]}",
            "{\"items\":[{\"userId\":\"%1$s\",\"score\":80,\"locked\":false},{\"userId\":\"%1$s\",\"score\":10,\"locked\":false}]}",
            "{\"items\":{\"userId\":\"%s\"}}",
            "{\"userId\":\"%s\",\"score\":80,\"locked\":false}",
            "[\"%s\"]",
            "not json %s"})
    void malformedAnswer_leavesEverySellerUnknown_notTrusted(String template) {
        responseBody = template.formatted(SELLER_A);

        ReputationLookup lookup = client.lookup(Set.of(SELLER_A));

        assertThat(lookup.complete()).isFalse();
        assertThat(lookup.bySeller()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 500, 503})
    void errorStatus_isAnIncompleteLookup(int code) {
        status = code;
        responseBody = items(item(SELLER_A, "80", "false"));

        ReputationLookup lookup = client.lookup(Set.of(SELLER_A));

        assertThat(lookup.complete()).isFalse();
        assertThat(lookup.bySeller()).isEmpty();
    }

    @Test
    void nonJsonContentType_isUnknown() {
        contentType = "text/html";
        responseBody = items(item(SELLER_A, "80", "false"));

        assertThat(client.lookup(Set.of(SELLER_A)).complete()).isFalse();
    }

    @Test
    void slowAnswer_isDroppedAtTheReadTimeout() {
        delayMs = 3_000L;

        long started = System.nanoTime();
        ReputationLookup lookup = client.lookup(Set.of(SELLER_A, SELLER_B));
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;

        assertThat(lookup.complete()).isFalse();
        assertThat(lookup.bySeller()).isEmpty();
        assertThat(elapsedMs).isLessThan(1_500);
    }

    @Test
    void serviceDown_returnsAnIncompleteLookup_neverThrows() {
        server.stop(0);
        ReputationSummaryClient down = client(url(), 400);

        ReputationLookup lookup = down.lookup(Set.of(SELLER_A, SELLER_B));

        assertThat(lookup.complete()).isFalse();
        assertThat(lookup.bySeller()).isEmpty();
    }

    @Test
    void noSellers_makesNoCall() {
        assertThat(client.lookup(Set.of())).isEqualTo(new ReputationLookup(Map.of(), true));
        assertThat(calls).isEmpty();
    }

    @Test
    void moreThanTheBatchLimit_makesNoCall_andIsIncomplete() {
        Set<String> tooMany = new LinkedHashSet<>();
        IntStream.rangeClosed(0, ReputationSummaryClient.MAX_BATCH)
                .forEach(i -> tooMany.add("00000000-0000-0000-0000-%012d".formatted(i)));

        ReputationLookup lookup = client.lookup(tooMany);

        assertThat(lookup.complete()).isFalse();
        assertThat(calls).isEmpty();
    }

    @Test
    void blankSecret_isRejectedAtStartup() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new ReputationSummaryClient(RestClient.builder(), url(), " ", 300, 400))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
