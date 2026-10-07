package com.agricontract.gateway;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * Boundary test for the gateway edge (verification matrix 13 and 14): a real gateway in front of a
 * fake downstream, asserting what the downstream actually receives.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayBoundaryTest {

    private static final String SECRET = "it-gateway-secret";
    private static final String VALID_TOKEN = "valid-token";

    private static final MockWebServer downstream = startDownstream();

    @DynamicPropertySource
    static void routeToDownstream(DynamicPropertyRegistry registry) {
        String url = "http://localhost:" + downstream.getPort();
        registry.add("USER_SERVICE_URL", () -> url);
        registry.add("PRODUCT_SERVICE_URL", () -> url);
        registry.add("CONTRACT_SERVICE_URL", () -> url);
        registry.add("ESCROW_SERVICE_URL", () -> url);
        registry.add("gateway.internal-secret", () -> SECRET);
    }

    @Autowired
    private WebTestClient client;

    @LocalServerPort
    private int port;

    @MockBean
    private ReactiveJwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() throws InterruptedException {
        while (downstream.takeRequest(10, TimeUnit.MILLISECONDS) != null) {
            // drain requests left over from a previous test
        }
        given(jwtDecoder.decode(anyString())).willReturn(Mono.error(new BadJwtException("invalid token")));
        given(jwtDecoder.decode(VALID_TOKEN)).willReturn(Mono.just(Jwt.withTokenValue(VALID_TOKEN)
                .header("alg", "RS256")
                .subject("user-123")
                .claim("email", "user@agri.test")
                .claim("realm_access", Map.of("roles", List.of("BUYER")))
                .build()));
    }

    @AfterAll
    static void tearDown() throws IOException {
        downstream.shutdown();
    }

    // Matrix 13: forged identity is ignored

    @Test
    void protectedRoute_forgedIdentityHeaders_downstreamReceivesJwtIdentity() throws InterruptedException {
        downstream.enqueue(new MockResponse().setResponseCode(200));

        client.get().uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                .header("X-User-Id", "attacker")
                .header("X-User-Role", "ADMIN")
                .header("X-Internal-Secret", "guessed")
                .header("X-Gateway-Secret", "forged")
                .exchange()
                .expectStatus().isOk();

        RecordedRequest received = downstream.takeRequest(2, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.getHeaders().values("X-User-Id")).containsExactly("user-123");
        assertThat(received.getHeaders().values("X-User-Role")).containsExactly("BUYER");
        assertThat(received.getHeaders().values("X-Gateway-Secret")).containsExactly(SECRET);
        assertThat(received.getHeader("X-Internal-Secret")).isNull();
        assertThat(received.getHeader(HttpHeaders.AUTHORIZATION)).isNull();
    }

    @Test
    void publicRoute_withoutToken_forwardsWithoutIdentityButWithGatewaySecret() throws InterruptedException {
        downstream.enqueue(new MockResponse().setResponseCode(200));

        client.get().uri("/api/v1/listings")
                .header("X-User-Id", "attacker")
                .header("X-User-Role", "ADMIN")
                .exchange()
                .expectStatus().isOk();

        RecordedRequest received = downstream.takeRequest(2, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.getHeader("X-User-Id")).isNull();
        assertThat(received.getHeader("X-User-Role")).isNull();
        assertThat(received.getHeaders().values("X-Gateway-Secret")).containsExactly(SECRET);
    }

    @Test
    void publicRoute_withInvalidToken_isRejected() throws InterruptedException {
        client.get().uri("/api/v1/listings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer bad-token")
                .exchange()
                .expectStatus().isUnauthorized();

        assertNotForwarded();
    }

    @Test
    void protectedRoute_withoutToken_isRejected() throws InterruptedException {
        client.get().uri("/api/v1/users/me")
                .exchange()
                .expectStatus().isUnauthorized();

        assertNotForwarded();
    }

    @Test
    void publicPath_withNonGetMethod_requiresToken() throws InterruptedException {
        client.post().uri("/api/v1/listings")
                .exchange()
                .expectStatus().isUnauthorized();

        assertNotForwarded();
    }

    // Matrix 14: internal routes are not public

    @ParameterizedTest
    @ValueSource(strings = {"", VALID_TOKEN})
    void internalRoute_isNotFoundAndNeverForwarded(String token) throws InterruptedException {
        WebTestClient.RequestHeadersSpec<?> request = client.get().uri("/internal/v1/users/user-123");
        if (!token.isEmpty()) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }

        request.exchange().expectStatus().isNotFound();

        assertNotForwarded();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/users/../../../internal/v1/users/user-123",
            "/api/v1/users/..%2F..%2F..%2Finternal/v1/users/user-123",
            "/api/v1/users/%2e%2e/%2e%2e/%2e%2e/internal/v1/users/user-123"
    })
    void pathTraversalTowardsInternalRoute_isNeverForwarded(String path) throws InterruptedException {
        downstream.enqueue(new MockResponse().setResponseCode(200));

        client.get().uri(URI.create("http://localhost:" + port + path))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                .exchange()
                .expectStatus().is4xxClientError();

        assertNotForwarded();
    }

    // Route map

    @Test
    void removedNotificationRoute_isNotFound() throws InterruptedException {
        client.get().uri("/api/v1/notifications/anything")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                .exchange()
                .expectStatus().isNotFound();

        assertNotForwarded();
    }

    @Test
    void categoryRoute_isForwardedToProductService() throws InterruptedException {
        downstream.enqueue(new MockResponse().setResponseCode(200));

        client.get().uri("/api/v1/categories")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                .exchange()
                .expectStatus().isOk();

        RecordedRequest received = downstream.takeRequest(2, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.getPath()).isEqualTo("/api/v1/categories");
    }

    private static void assertNotForwarded() throws InterruptedException {
        assertThat(downstream.takeRequest(300, TimeUnit.MILLISECONDS))
                .as("request must not reach the downstream service")
                .isNull();
    }

    private static MockWebServer startDownstream() {
        MockWebServer server = new MockWebServer();
        try {
            server.start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return server;
    }
}
