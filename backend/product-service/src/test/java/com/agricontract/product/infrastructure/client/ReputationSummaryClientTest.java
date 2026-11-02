package com.agricontract.product.infrastructure.client;

import com.agricontract.product.application.dto.ReputationLookup;
import com.agricontract.product.application.dto.ReputationViewer;
import com.agricontract.product.application.dto.SellerReputation;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boundary of the reputation-service public summary (golden-flow-api.yaml ReputationPublicSummary) against a real
 * HTTP stub: headers sent, fields kept, and every failure mode degrading to "unknown" instead of throwing.
 */
class ReputationSummaryClientTest {
    private static final String SELLER_A = "9b0c8f0e-2f43-4a51-9d8c-1d6f3f1e7a10";
    private static final String SELLER_B = "3f2a1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d";
    private static final ReputationViewer VIEWER = new ReputationViewer("buyer-uuid", List.of("BUYER"));

    private HttpServer server;
    private ReputationSummaryClient client;
    private final Map<String, String> bodies = new ConcurrentHashMap<>();
    private final Map<String, Integer> statuses = new ConcurrentHashMap<>();
    private final Map<String, Long> delays = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> seenHeaders = new ConcurrentHashMap<>();
    private volatile String contentType = "application/json";

    private static String summary(String userId, String score, String locked) {
        return """
                {"userId":"%s","score":%s,"locked":%s,"lockHistory":[{"lockedAt":"2026-01-01T00:00:00Z",
                 "lockedUntil":"2026-02-01T00:00:00Z","lockDurationDays":31,"penalizedRole":"SELLER",
                 "status":"EXPIRED"}]}""".formatted(userId, score, locked);
    }

    @BeforeEach
    void startReputationStub() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/api/v1/reputation/", exchange -> {
            String userId = exchange.getRequestURI().getPath().split("/")[4];
            seenHeaders.put(userId, Map.of(
                    "gateway", String.valueOf(exchange.getRequestHeaders().getFirst("X-Gateway-Secret")),
                    "user", String.valueOf(exchange.getRequestHeaders().getFirst("X-User-Id")),
                    "role", String.valueOf(exchange.getRequestHeaders().getFirst("X-User-Role")),
                    "path", exchange.getRequestURI().getPath()));
            long delay = delays.getOrDefault(userId, 0L);
            if (delay > 0) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = bodies.getOrDefault(userId, summary(userId, "null", "false"))
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(statuses.getOrDefault(userId, 200), bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        client = client("http://127.0.0.1:" + server.getAddress().getPort(), 400);
    }

    private static ReputationSummaryClient client(String url, int budgetMs) {
        return client(url, 300, budgetMs);
    }

    private static ReputationSummaryClient client(String url, int readTimeoutMs, int budgetMs) {
        return new ReputationSummaryClient(RestClient.builder(), url, "gw-secret", 300, readTimeoutMs, budgetMs);
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    @Test
    void readsThePublicSummaryAsTheViewer_andKeepsOnlyScoreAndLocked() {
        bodies.put(SELLER_A, summary(SELLER_A, "87", "true"));

        ReputationLookup lookup = client.lookup(Set.of(SELLER_A, SELLER_B), VIEWER);

        assertThat(lookup.complete()).isTrue();
        assertThat(lookup.bySeller()).containsExactlyInAnyOrderEntriesOf(Map.of(
                SELLER_A, new SellerReputation(87, true),
                SELLER_B, new SellerReputation(null, false)));
        assertThat(seenHeaders.get(SELLER_A)).containsAllEntriesOf(Map.of(
                "gateway", "gw-secret", "user", "buyer-uuid", "role", "BUYER",
                "path", "/api/v1/reputation/" + SELLER_A + "/public-summary"));
    }

    @Test
    void integralDecimalScore_isAccepted() {
        bodies.put(SELLER_A, summary(SELLER_A, "75.0", "false"));

        assertThat(client.lookup(Set.of(SELLER_A), VIEWER).bySeller().get(SELLER_A))
                .isEqualTo(new SellerReputation(75, false));
    }

    @Test
    void oneSellerFailing_leavesOnlyThatSellerUnknown() {
        statuses.put(SELLER_B, 500);

        ReputationLookup lookup = client.lookup(Set.of(SELLER_A, SELLER_B), VIEWER);

        assertThat(lookup.complete()).isFalse();
        assertThat(lookup.bySeller()).containsOnlyKeys(SELLER_A);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"userId\":\"%s\",\"score\":101,\"locked\":false,\"lockHistory\":[]}",
            "{\"userId\":\"%s\",\"score\":-1,\"locked\":false,\"lockHistory\":[]}",
            "{\"userId\":\"%s\",\"score\":80.5,\"locked\":false,\"lockHistory\":[]}",
            "{\"userId\":\"%s\",\"score\":\"80\",\"locked\":false,\"lockHistory\":[]}",
            "{\"userId\":\"%s\",\"locked\":false,\"lockHistory\":[]}",
            "{\"userId\":\"%s\",\"score\":80,\"lockHistory\":[]}",
            "{\"userId\":\"%s\",\"score\":80,\"locked\":\"no\",\"lockHistory\":[]}",
            "{\"userId\":\"someone-else\",\"score\":80,\"locked\":false,\"lockHistory\":[]}",
            "[\"%s\"]",
            "not json %s"})
    void malformedSummary_isUnknown_notTrusted(String template) {
        bodies.put(SELLER_A, template.formatted(SELLER_A));

        ReputationLookup lookup = client.lookup(Set.of(SELLER_A), VIEWER);

        assertThat(lookup.complete()).isFalse();
        assertThat(lookup.bySeller()).isEmpty();
    }

    @Test
    void nonJsonContentType_isUnknown() {
        contentType = "text/html";

        assertThat(client.lookup(Set.of(SELLER_A), VIEWER).complete()).isFalse();
    }

    @Test
    void slowSeller_isDroppedAtTheBudget_evenBeforeItsReadTimeout() {
        delays.put(SELLER_B, 3_000L);
        ReputationSummaryClient patientReads = client("http://127.0.0.1:" + server.getAddress().getPort(), 5_000, 400);

        long started = System.nanoTime();
        ReputationLookup lookup = patientReads.lookup(Set.of(SELLER_A, SELLER_B), VIEWER);
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;

        assertThat(lookup.complete()).isFalse();
        assertThat(lookup.bySeller()).containsOnlyKeys(SELLER_A);
        assertThat(elapsedMs).isLessThan(1_500);
    }

    @Test
    void serviceDown_returnsAnIncompleteLookup_neverThrows() {
        server.stop(0);
        ReputationSummaryClient down = client("http://127.0.0.1:" + server.getAddress().getPort(), 400);

        ReputationLookup lookup = down.lookup(Set.of(SELLER_A, SELLER_B), VIEWER);

        assertThat(lookup.complete()).isFalse();
        assertThat(lookup.bySeller()).isEmpty();
    }

    @Test
    void noSellers_makesNoCall() {
        assertThat(client.lookup(Set.of(), VIEWER)).isEqualTo(new ReputationLookup(Map.of(), true));
        assertThat(seenHeaders).isEmpty();
    }
}
