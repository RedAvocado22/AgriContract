package com.agricontract.inspection.infrastructure.user;

import com.agricontract.inspection.common.exception.EligibilityUnavailableException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class InternalUserEligibilityClientTest {
    private static final String ID = "1349fbf6-44b6-450c-b01d-4b5abaf7c326";
    private static final String VALID = """
            {"userId":"%s","organizationName":"Inspection organization","email":"private@example.test",
             "role":"INSPECTOR","verificationStatus":"VERIFIED","authorizationExpiresAt":null,
             "lockedUntil":"2099-01-01T00:00:00Z"}
            """.formatted(ID);
    private HttpServer server;
    private InternalUserEligibilityClient client;
    private volatile int status = 200;
    private volatile String response = VALID;
    private final AtomicReference<String> requestedPath = new AtomicReference<>();
    private final AtomicReference<String> internalSecret = new AtomicReference<>();

    @BeforeEach
    void startUserServiceStub() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestedPath.set(exchange.getRequestURI().getPath());
            internalSecret.set(exchange.getRequestHeaders().getFirst("X-Internal-Secret"));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var stream = exchange.getResponseBody()) {
                stream.write(bytes);
            }
        });
        server.start();
        client = new InternalUserEligibilityClient(RestClient.builder(),
                "http://127.0.0.1:" + server.getAddress().getPort(), "test-service-secret", 500, 500);
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    @Test
    void usesFrozenInternalPathAndServiceAuthenticationWithoutApplyingBuyerSellerLock() {
        var profile = client.load(ID);
        assertThat(requestedPath.get()).isEqualTo("/internal/v1/users/" + ID);
        assertThat(internalSecret.get()).isEqualTo("test-service-secret");
        assertThat(profile.isEligibleAt(Instant.parse("2026-10-08T00:00:00Z"))).isTrue();
    }

    @Test
    void parsesAuthoritativeLicenseExpiry() {
        response = VALID.replace("\"authorizationExpiresAt\":null",
                "\"authorizationExpiresAt\":\"2027-06-30T16:59:59.999Z\"");
        assertThat(client.load(ID).authorizationExpiresAt())
                .isEqualTo(Instant.parse("2027-06-30T16:59:59.999Z"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "[]", "not JSON",
            "{\"userId\":\"x\",\"role\":\"INSPECTOR\",\"verificationStatus\":\"VERIFIED\",\"authorizationExpiresAt\":null}"})
    void incompleteOrMismatchedResponsesFailClosed(String body) {
        response = body;
        assertThatThrownBy(() -> client.load(ID)).isInstanceOf(EligibilityUnavailableException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"userId", "role", "verificationStatus", "authorizationExpiresAt"})
    void missingRequiredEligibilityInformationFailsClosed(String field) throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var body = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(VALID);
        body.remove(field);
        response = mapper.writeValueAsString(body);
        assertThatThrownBy(() -> client.load(ID)).isInstanceOf(EligibilityUnavailableException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "false", "\"\"", "\"invalid timestamp\""})
    void malformedExpiryMustNotBecomeUnlimited(String expiry) {
        response = VALID.replace("\"authorizationExpiresAt\":null", "\"authorizationExpiresAt\":" + expiry);
        assertThatThrownBy(() -> client.load(ID)).isInstanceOf(EligibilityUnavailableException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {302, 401, 403, 404, 500, 503})
    void dependencyHttpErrorsFailClosedWithoutLeakingResponse(int httpStatus) {
        status = httpStatus;
        assertThatThrownBy(() -> client.load(ID)).isInstanceOf(EligibilityUnavailableException.class)
                .hasMessage("Authoritative inspector eligibility could not be validated");
    }

    @Test
    void connectionFailureFailsClosed() {
        server.stop(0);
        assertThatThrownBy(() -> client.load(ID)).isInstanceOf(EligibilityUnavailableException.class);
    }

    @Test
    void emptySecretIsRejected() {
        assertThatThrownBy(() -> new InternalUserEligibilityClient(RestClient.builder(),
                "http://localhost", " ", 500, 500)).isInstanceOf(IllegalArgumentException.class);
    }
}
