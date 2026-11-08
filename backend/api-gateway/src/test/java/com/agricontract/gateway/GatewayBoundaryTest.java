package com.agricontract.gateway;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.QueueDispatcher;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
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
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
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
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * Boundary test for the gateway edge (verification matrix 13, 14, 16, 17b, 17c and gateway design 3.4/5):
 * a real gateway in front of a fake downstream, asserting what the downstream actually receives.
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
        registry.add("BANK_SERVICE_URL", () -> url);
        registry.add("INSPECTION_SERVICE_URL", () -> url);
        registry.add("REPUTATION_SERVICE_URL", () -> url);
        registry.add("PRICING_SERVICE_URL", () -> url);
        registry.add("AUDIT_SERVICE_URL", () -> url);
        registry.add("ANALYTICS_SERVICE_URL", () -> url);
        registry.add("gateway.internal-secret", () -> SECRET);
        registry.add("gateway.cors.allowed-origins", () -> ALLOWED_ORIGIN);
        // One trusted proxy lets each rate-limit test pick its own client IP through X-Forwarded-For.
        registry.add("gateway.rate-limit.trusted-proxies", () -> "1");
        registry.add("spring.cloud.gateway.httpclient.response-timeout", () -> "1s");
    }

    private static final String ALLOWED_ORIGIN = "http://app.agri.test";
    private static final AtomicInteger CLIENT_IPS = new AtomicInteger(1);

    /** A fresh client IP per test, so per-IP buckets never leak between tests sharing the context. */
    private static String newClientIp() {
        return "10.0.0." + CLIENT_IPS.getAndIncrement();
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
        // Fresh response queue per test: a response a previous test enqueued but never used must not answer
        // this test's request. Fail-fast still records an unexpected forward and answers it at once.
        QueueDispatcher dispatcher = new QueueDispatcher();
        dispatcher.setFailFast(true);
        downstream.setDispatcher(dispatcher);
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
        for (String path : List.of("/internal", "/internal/v1/users/user-123", "/internal/v1/bank/ledger",
                "/internal/v1/audit/records", "/internal/v1/files/file-1", "/internal/v1/notifications/otp-email")) {
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
            new StaffAction("POST", CONTRACT + "/activation/cancel/initiate", "BUYER", "SELLER"),
            new StaffAction("POST", CONTRACT + "/activation/cancel/verify", "BUYER", "SELLER"),
            new StaffAction("DELETE", CONTRACT + "/activation/cancel", "BUYER", "SELLER"),
            new StaffAction("POST", CONTRACT + "/milestones/" + LISTING_ID + "/funding/retry", "OPERATOR"),
            new StaffAction("GET", "/api/v1/admin/categories", "OPERATOR"),
            new StaffAction("POST", "/api/v1/admin/categories/" + LISTING_ID + "/approve", "OPERATOR"),
            new StaffAction("POST", "/api/v1/admin/products/" + LISTING_ID + "/moderate", "OPERATOR"),
            new StaffAction("POST", "/api/v1/admin/reputation/actions/propose", "OPERATOR"),
            new StaffAction("POST", "/api/v1/admin/reputation/actions/" + LISTING_ID + "/approve", "ARBITRATOR"),
            new StaffAction("POST", "/api/v1/admin/reputation/actions/" + LISTING_ID + "/reject", "ARBITRATOR"),
            new StaffAction("GET", "/api/v1/admin/reputation/actions", "OPERATOR", "ARBITRATOR"),
            new StaffAction("POST", "/api/v1/inspections/" + LISTING_ID + "/milestones/" + LISTING_ID + "/reports",
                    "INSPECTOR"),
            new StaffAction("GET", "/api/v1/inspector/assignments", "INSPECTOR"),
            new StaffAction("POST", "/api/v1/admin/inspections/cases", "OPERATOR"),
            new StaffAction("POST", "/api/v1/prices/manual", "OPERATOR"),
            new StaffAction("GET", "/api/v1/admin/prices/sources", "OPERATOR"),
            new StaffAction("GET", "/api/v1/analytics/terminations?year=2026", "ADMIN"),
            new StaffAction("GET", "/api/v1/analytics/escrow-effectiveness?month=2026-10", "ADMIN"),
            new StaffAction("GET", "/api/v1/admin/analytics/overview", "ADMIN"),
            new StaffAction("GET", "/api/v1/admin/security/system-locks", "ADMIN"),
            new StaffAction("GET", "/api/v1/admin/audit/contracts/" + LISTING_ID + "/export", "ADMIN")
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

    // Public Phase 2 routes: reference prices and audit hash need no JWT

    @Test
    void publicPriceRoutes_withoutToken_areForwardedAnonymously() throws InterruptedException {
        for (String path : List.of("/api/v1/prices/COFFEE/DAK_LAK", "/api/v1/prices/COFFEE/DAK_LAK/items")) {
            downstream.enqueue(new MockResponse().setResponseCode(200));

            client.get().uri(path)
                    .header("X-User-Id", "attacker")
                    .exchange()
                    .expectStatus().isOk();

            RecordedRequest received = downstream.takeRequest(2, TimeUnit.SECONDS);
            assertThat(received).isNotNull();
            assertThat(received.getPath()).isEqualTo(path);
            assertThat(received.getHeader("X-User-Id")).isNull();
        }
    }

    @Test
    void manualPrice_withoutToken_isRejected() throws InterruptedException {
        client.post().uri("/api/v1/prices/manual")
                .exchange()
                .expectStatus().isUnauthorized();

        assertNotForwarded();
    }

    @Test
    void auditHash_withoutJwt_isForwardedWithApiKeyUntouched() throws InterruptedException {
        downstream.enqueue(new MockResponse().setResponseCode(200));

        client.get().uri("/api/v1/security/audit-hash?contractId=" + LISTING_ID)
                .header("X-Forwarded-For", newClientIp())
                .header("X-Api-Key", "verifier-key")
                .header("X-User-Role", "ADMIN")
                .exchange()
                .expectStatus().isOk();

        RecordedRequest received = downstream.takeRequest(2, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.getHeaders().values("X-Api-Key")).containsExactly("verifier-key");
        assertThat(received.getHeader("X-User-Role")).isNull();
        assertThat(received.getHeaders().values("X-Gateway-Secret")).containsExactly(SECRET);
    }

    @Test
    void reputationPublicSummary_requiresToken() throws InterruptedException {
        client.get().uri("/api/v1/reputation/user-456/public-summary")
                .exchange()
                .expectStatus().isUnauthorized();
        assertNotForwarded();

        downstream.enqueue(new MockResponse().setResponseCode(200));
        client.get().uri("/api/v1/reputation/user-456/public-summary")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                .exchange()
                .expectStatus().isOk();
        assertThat(downstream.takeRequest(2, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void escrowStatement_isParticipantAuthenticated() throws InterruptedException {
        String path = "/api/v1/escrows/statements?from=2026-10-01T00:00:00Z&to=2026-10-31T23:59:59Z";
        client.get().uri(path)
                .exchange()
                .expectStatus().isUnauthorized();
        assertNotForwarded();

        downstream.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-Type", "text/csv"));
        client.get().uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + SELLER_TOKEN)
                .exchange()
                .expectStatus().isOk();
        RecordedRequest received = downstream.takeRequest(2, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.getHeaders().values("X-User-Role")).containsExactly("SELLER");
    }

    // External Verifier (gateway design 3.4): signed body, no JWT, 64 KB cap, per-IP bucket

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/security/emergency-lock", "/api/v1/security/emergency-unlock"})
    void verifierRoute_forwardsRawBodyWithoutUserIdentity_evenWithStrayBearer(String path) throws InterruptedException {
        String signed = "{\"keyId\":\"verifier-1\",\"signedPayload\":\"eyJhY3Rpb24iOiJMT0NLIn0\",\"signature\":\"MEUCIQ\"}";
        downstream.enqueue(new MockResponse().setResponseCode(204));

        client.post().uri(path)
                .header("X-Forwarded-For", newClientIp())
                .header(HttpHeaders.AUTHORIZATION, "Bearer bad-token")
                .header("X-User-Id", "attacker")
                .header("X-User-Role", "ADMIN")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue(signed)
                .exchange()
                .expectStatus().isNoContent();

        RecordedRequest received = downstream.takeRequest(2, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.getPath()).isEqualTo(path);
        assertThat(received.getBody().readUtf8()).isEqualTo(signed);
        assertThat(received.getHeader(HttpHeaders.AUTHORIZATION)).isNull();
        assertThat(received.getHeader("X-User-Id")).isNull();
        assertThat(received.getHeader("X-User-Role")).isNull();
        assertThat(received.getHeaders().values("X-Gateway-Secret")).containsExactly(SECRET);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PUT", "DELETE"})
    void verifierRoute_otherMethod_isNotFound(String method) throws InterruptedException {
        client.method(HttpMethod.valueOf(method)).uri("/api/v1/security/emergency-lock")
                .header("X-Forwarded-For", newClientIp())
                .exchange()
                .expectStatus().isNotFound();

        assertNotForwarded();
    }

    @Test
    void verifierRoute_bodyAtCap_isForwarded() throws InterruptedException {
        downstream.enqueue(new MockResponse().setResponseCode(204));

        client.post().uri("/api/v1/security/emergency-lock")
                .header("X-Forwarded-For", newClientIp())
                .bodyValue(new byte[VERIFIER_CAP])
                .exchange()
                .expectStatus().isNoContent();

        RecordedRequest received = downstream.takeRequest(2, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.getBodySize()).isEqualTo(VERIFIER_CAP);
    }

    @Test
    void verifierRoute_declaredBodyOverCap_isRejectedWith413() throws InterruptedException {
        client.post().uri("/api/v1/security/emergency-lock")
                .header("X-Forwarded-For", newClientIp())
                .bodyValue(new byte[VERIFIER_CAP + 1])
                .exchange()
                .expectStatus().isEqualTo(413);

        assertNotForwarded();
    }

    @Test
    void verifierRoute_chunkedBodyOverCap_isRejectedWith413() throws InterruptedException {
        client.post().uri("/api/v1/security/emergency-unlock")
                .header("X-Forwarded-For", newClientIp())
                .body(BodyInserters.fromDataBuffers(chunks(VERIFIER_CAP / 2, VERIFIER_CAP / 2 + 1)))
                .exchange()
                .expectStatus().isEqualTo(413);

        assertNotForwarded();
    }

    @Test
    void verifierRoute_chunkedBodyUnderCap_isForwardedByteForByte() throws InterruptedException {
        downstream.enqueue(new MockResponse().setResponseCode(204));

        client.post().uri("/api/v1/security/emergency-unlock")
                .header("X-Forwarded-For", newClientIp())
                .body(BodyInserters.fromDataBuffers(chunks(1000, 2000)))
                .exchange()
                .expectStatus().isNoContent();

        RecordedRequest received = downstream.takeRequest(2, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.getHeader(HttpHeaders.CONTENT_LENGTH)).isEqualTo("3000");
        byte[] body = received.getBody().readByteArray();
        assertThat(body).hasSize(3000);
        for (int i = 0; i < body.length; i++) {
            assertThat(body[i]).isEqualTo((byte) i);
        }
    }

    @Test
    void verifierRoute_overPerIpLimit_isRejectedWith429AndNeverForwarded() throws InterruptedException {
        String ip = newClientIp();
        for (int i = 0; i < VERIFIER_LIMIT; i++) {
            downstream.enqueue(new MockResponse().setResponseCode(204));
            client.post().uri("/api/v1/security/emergency-lock")
                    .header("X-Forwarded-For", ip)
                    .bodyValue("{}")
                    .exchange()
                    .expectStatus().isNoContent();
        }
        for (int i = 0; i < VERIFIER_LIMIT; i++) {
            assertThat(downstream.takeRequest(2, TimeUnit.SECONDS)).isNotNull();
        }

        client.post().uri("/api/v1/security/emergency-unlock")
                .header("X-Forwarded-For", ip)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().exists("Retry-After");
        assertNotForwarded();

        // The bucket is per client IP: another verifier is unaffected.
        downstream.enqueue(new MockResponse().setResponseCode(204));
        client.post().uri("/api/v1/security/emergency-lock")
                .header("X-Forwarded-For", newClientIp())
                .bodyValue("{}")
                .exchange()
                .expectStatus().isNoContent();
        assertThat(downstream.takeRequest(2, TimeUnit.SECONDS)).isNotNull();
    }

    // Edge policies (gateway design 5.1, 5.2, 5.4)

    @Test
    void jsonRoute_bodyOverDefaultCap_isRejectedWith413() throws InterruptedException {
        client.post().uri("/api/v1/contracts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                .bodyValue(new byte[1024 * 1024 + 1])
                .exchange()
                .expectStatus().isEqualTo(413);

        assertNotForwarded();
    }

    @Test
    void writeRequest_downstreamDropsConnection_isSentExactlyOnce() throws InterruptedException {
        downstream.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));

        client.post().uri("/api/v1/contracts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                .bodyValue("{\"listingId\":\"" + LISTING_ID + "\"}")
                .exchange()
                .expectStatus().is5xxServerError();

        assertThat(downstream.takeRequest(2, TimeUnit.SECONDS)).isNotNull();
        assertNotForwarded();
    }

    @Test
    void downstreamTimeout_isGatewayTimeoutAndNotFabricated() {
        downstream.enqueue(new MockResponse().setResponseCode(200).setHeadersDelay(3, TimeUnit.SECONDS));

        client.get().uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                .exchange()
                .expectStatus().isEqualTo(504);
    }

    @Test
    void corsPreflight_fromAllowedOrigin_isAnsweredWithoutToken() throws InterruptedException {
        client.options().uri("/api/v1/contracts")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Idempotency-Key")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN)
                .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS);

        assertNotForwarded();
    }

    @Test
    void corsPreflight_fromUnknownOrigin_isForbidden() throws InterruptedException {
        client.options().uri("/api/v1/contracts")
                .header(HttpHeaders.ORIGIN, "http://evil.test")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);

        assertNotForwarded();
    }

    private static final int VERIFIER_CAP = 64 * 1024;
    private static final int VERIFIER_LIMIT = 10;

    /** Two body chunks with no Content-Length, so the client sends Transfer-Encoding: chunked. */
    private static Flux<DataBuffer> chunks(int first, int second) {
        byte[] bytes = new byte[first + second];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) i;
        }
        DefaultDataBufferFactory factory = DefaultDataBufferFactory.sharedInstance;
        return Flux.just(
                factory.wrap(java.util.Arrays.copyOfRange(bytes, 0, first)),
                factory.wrap(java.util.Arrays.copyOfRange(bytes, first, bytes.length)));
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
