package com.agricontract.gateway;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

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
    private static final String SELLER_TOKEN = "seller-token";
    private static final String ADMIN_TOKEN = "admin-token";
    private static final String OPERATOR_TOKEN = "operator-token";
    private static final String ARBITRATOR_TOKEN = "arbitrator-token";
    private static final String INSPECTOR_TOKEN = "inspector-token";
    private static final String OPERATOR_ARBITRATOR_TOKEN = "operator-arbitrator-token";
    private static final String EXPIRED_TOKEN = "expired-token";
    private static final String LISTING_ID = "3f2b8c1e-7a4d-4e9b-9c2f-1d5e6a7b8c9d";

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
        given(jwtDecoder.decode(EXPIRED_TOKEN)).willReturn(Mono.error(new JwtValidationException(
                "Jwt expired", List.of(new OAuth2Error("invalid_token", "Jwt expired", null)))));
        givenToken(VALID_TOKEN, "BUYER");
        givenToken(SELLER_TOKEN, "SELLER");
        givenToken(ADMIN_TOKEN, "ADMIN");
        givenToken(OPERATOR_TOKEN, "OPERATOR");
        givenToken(ARBITRATOR_TOKEN, "ARBITRATOR");
        givenToken(INSPECTOR_TOKEN, "INSPECTOR");
        givenToken(OPERATOR_ARBITRATOR_TOKEN, "OPERATOR", "ARBITRATOR");
    }

    private void givenToken(String token, String... roles) {
        List<String> realmRoles = new ArrayList<>(List.of("default-roles-agricontract", "offline_access"));
        realmRoles.addAll(List.of(roles));
        given(jwtDecoder.decode(token)).willReturn(Mono.just(Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .subject("user-123")
                .claim("email", "user@agri.test")
                .claim("realm_access", Map.of("roles", realmRoles))
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

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/listings/" + LISTING_ID, "/api/v1/products/" + LISTING_ID})
    void publicDetailRoute_withUuid_isForwardedAnonymously(String path) throws InterruptedException {
        downstream.enqueue(new MockResponse().setResponseCode(200));

        client.get().uri(path)
                .exchange()
                .expectStatus().isOk();

        RecordedRequest received = downstream.takeRequest(2, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.getPath()).isEqualTo(path);
        assertThat(received.getHeader("X-User-Id")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/listings/seller", "/api/v1/products/mine", "/api/v1/listings/not-a-uuid"})
    void reservedSiblingOfPublicDetailRoute_withoutToken_isRejected(String path) throws InterruptedException {
        client.get().uri(path)
                .exchange()
                .expectStatus().isUnauthorized();

        assertNotForwarded();
    }

    @Test
    void reservedSiblingOfPublicDetailRoute_withToken_isForwardedWithIdentity() throws InterruptedException {
        downstream.enqueue(new MockResponse().setResponseCode(200));

        client.get().uri("/api/v1/listings/seller")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + SELLER_TOKEN)
                .exchange()
                .expectStatus().isOk();

        RecordedRequest received = downstream.takeRequest(2, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.getHeaders().values("X-User-Role")).containsExactly("SELLER");
    }

    // Matrix 14: internal routes are not public

    @ParameterizedTest
    @ValueSource(strings = {"", VALID_TOKEN, ADMIN_TOKEN, "bad-token", EXPIRED_TOKEN})
    void internalRoute_isNotFoundAndNeverForwarded_regardlessOfBearer(String token) throws InterruptedException {
        for (String path : List.of("/internal", "/internal/v1/users/user-123")) {
            WebTestClient.RequestHeadersSpec<?> request = client.get().uri(path);
            if (!token.isEmpty()) {
                request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            }

            request.exchange().expectStatus().isNotFound();
        }

        assertNotForwarded();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "bad-token"})
    void internalRoute_nonGetMethod_isNotFoundAndNeverForwarded(String token) throws InterruptedException {
        WebTestClient.RequestHeadersSpec<?> request = client.post().uri("/internal/v1/users/user-123/verify");
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

    @Test
    void plotRoute_isForwardedToProductService() throws InterruptedException {
        downstream.enqueue(new MockResponse().setResponseCode(201));

        client.post().uri("/api/v1/plots")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                .exchange()
                .expectStatus().isCreated();

        RecordedRequest received = downstream.takeRequest(2, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.getPath()).isEqualTo("/api/v1/plots");
    }

    // Identity: every canonical application role survives, alone or combined

    @ParameterizedTest
    @ValueSource(strings = {"BUYER", "SELLER", "ADMIN", "OPERATOR", "ARBITRATOR", "INSPECTOR"})
    void authenticatedRoute_eachApplicationRole_isForwardedWithThatRole(String role) throws InterruptedException {
        downstream.enqueue(new MockResponse().setResponseCode(200));

        client.get().uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(role))
                .exchange()
                .expectStatus().isOk();

        RecordedRequest received = downstream.takeRequest(2, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.getHeaders().values("X-User-Role")).containsExactly(role);
    }

    // user-service rejects OPERATOR+ARBITRATOR; the gateway must forward both so that guard can see the pair.
    @ParameterizedTest
    @CsvSource({
            "GET, /api/v1/users/me",
            "POST, /api/v1/admin/users/user-456/verify"
    })
    void multiRoleToken_forwardsFullRoleSet(String method, String path) throws InterruptedException {
        downstream.enqueue(new MockResponse().setResponseCode(200));

        client.method(HttpMethod.valueOf(method)).uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + OPERATOR_ARBITRATOR_TOKEN)
                .exchange()
                .expectStatus().isOk();

        RecordedRequest received = downstream.takeRequest(2, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.getHeaders().values("X-User-Role")).containsExactly("OPERATOR,ARBITRATOR");
    }

    // Staff gate (gateway design 3.3, SRS v1.6): each routed staff action takes exactly its own role

    private static final List<String> ALL_ROLES = List.of("BUYER", "SELLER", "ADMIN", "OPERATOR", "ARBITRATOR", "INSPECTOR");
    private static final String CONTRACT = "/api/v1/contracts/" + LISTING_ID;

    private static final List<StaffAction> STAFF_ACTIONS = List.of(
            new StaffAction("GET", "/api/v1/admin/users?verificationStatus=PENDING", "OPERATOR"),
            new StaffAction("GET", "/api/v1/admin/users/user-456", "OPERATOR"),
            new StaffAction("POST", "/api/v1/admin/users/user-456/verify", "OPERATOR"),
            new StaffAction("POST", "/api/v1/admin/users/user-456/reject", "OPERATOR"),
            new StaffAction("PUT", "/api/v1/admin/users/user-456/role", "ADMIN"),
            new StaffAction("POST", CONTRACT + "/breach-cases/" + LISTING_ID + "/review", "ARBITRATOR", "INSPECTOR"),
            new StaffAction("POST", CONTRACT + "/breach-cases/" + LISTING_ID + "/resolve", "ARBITRATOR", "INSPECTOR"),
            new StaffAction("POST", CONTRACT + "/milestones/" + LISTING_ID + "/force-majeure/resolve", "ARBITRATOR"),
            new StaffAction("POST", CONTRACT + "/activation/retry-deposit-lock", "OPERATOR"),
            new StaffAction("POST", CONTRACT + "/activation/mark-failed", "OPERATOR"),
            new StaffAction("POST", CONTRACT + "/milestones/" + LISTING_ID + "/funding/retry", "OPERATOR")
    );

    private record StaffAction(String method, String path, String... allowedRoles) {
    }

    static Stream<Arguments> allowedStaffRequests() {
        return STAFF_ACTIONS.stream().flatMap(action -> Stream.of(action.allowedRoles())
                .map(role -> Arguments.of(role, action.method(), action.path())));
    }

    static Stream<Arguments> wrongRoleStaffRequests() {
        return STAFF_ACTIONS.stream().flatMap(action -> ALL_ROLES.stream()
                .filter(role -> !List.of(action.allowedRoles()).contains(role))
                .map(role -> Arguments.of(role, action.method(), action.path())));
    }

    @ParameterizedTest(name = "{0} {1} {2}")
    @MethodSource("allowedStaffRequests")
    void staffAction_withItsRole_isForwardedWithJwtIdentity(String role, String method, String path)
            throws InterruptedException {
        downstream.enqueue(new MockResponse().setResponseCode(200));

        client.method(HttpMethod.valueOf(method)).uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(role))
                .exchange()
                .expectStatus().isOk();

        RecordedRequest received = downstream.takeRequest(2, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.getMethod()).isEqualTo(method);
        assertThat(received.getPath()).isEqualTo(path);
        assertThat(received.getHeaders().values("X-User-Id")).containsExactly("user-123");
        assertThat(received.getHeaders().values("X-User-Role")).containsExactly(role);
    }

    @ParameterizedTest(name = "{0} {1} {2}")
    @MethodSource("wrongRoleStaffRequests")
    void staffAction_withAnyOtherRole_isForbiddenAndNeverForwarded(String role, String method, String path)
            throws InterruptedException {
        client.method(HttpMethod.valueOf(method)).uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(role))
                .header("X-User-Role", "ADMIN,OPERATOR,ARBITRATOR")
                .exchange()
                .expectStatus().isForbidden();

        assertNotForwarded();
    }

    // Exact staff matchers must not swallow the Buyer/Seller contract wildcard next to them.
    @Test
    void participantContractAction_nextToStaffActions_staysAuthenticatedOnly() throws InterruptedException {
        downstream.enqueue(new MockResponse().setResponseCode(200));

        client.post().uri(CONTRACT + "/breach-cases/" + LISTING_ID + "/contest")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                .exchange()
                .expectStatus().isOk();

        assertThat(downstream.takeRequest(2, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void adminUserRoute_withoutToken_isRejected() throws InterruptedException {
        client.post().uri("/api/v1/admin/users/user-456/verify")
                .exchange()
                .expectStatus().isUnauthorized();

        assertNotForwarded();
    }

    private static String tokenFor(String role) {
        return switch (role) {
            case "BUYER" -> VALID_TOKEN;
            case "SELLER" -> SELLER_TOKEN;
            case "ADMIN" -> ADMIN_TOKEN;
            case "OPERATOR" -> OPERATOR_TOKEN;
            case "ARBITRATOR" -> ARBITRATOR_TOKEN;
            case "INSPECTOR" -> INSPECTOR_TOKEN;
            default -> throw new IllegalArgumentException(role);
        };
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
