package com.agricontract.inspection.infrastructure.client;

import com.agricontract.inspection.application.exception.EligibilityUnavailableException;
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
    private final ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> logs =
            new ch.qos.logback.core.read.ListAppender<>();
    private final ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(InternalUserEligibilityClient.class);
    private String expectedCategory;
    private volatile java.util.concurrent.CountDownLatch responseGate;
    private volatile boolean interruptBody;
    private HttpServer server;
    private InternalUserEligibilityClient client;
    private volatile int status = 200;
    private volatile String response = VALID;
    private volatile String contentType = "application/json";
    private final AtomicReference<String> requestedPath = new AtomicReference<>();
    private final AtomicReference<String> internalSecret = new AtomicReference<>();

    @BeforeEach
    void startUserServiceStub() throws Exception {
        logs.start();
        logger.addAppender(logs);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestedPath.set(exchange.getRequestURI().getPath());
            internalSecret.set(exchange.getRequestHeaders().getFirst("X-Internal-Secret"));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            awaitResponseGate();
            exchange.sendResponseHeaders(status, bytes.length);
            if (interruptBody) {
                exchange.close();
                return;
            }
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
        if (responseGate != null) responseGate.countDown();
        server.stop(0);
        logger.detachAppender(logs);
        logs.stop();
        if (expectedCategory == null) {
            assertThat(logs.list).isEmpty();
        } else {
            assertThat(logs.list).hasSize(1);
            var event = logs.list.getFirst();
            assertThat(event.getFormattedMessage())
                    .isEqualTo("eligibility_dependency_failure category=" + expectedCategory)
                    .doesNotContain(ID, "private@example.test", "test-service-secret", "127.0.0.1", "sentinel");
            assertThat(event.getArgumentArray()).containsExactly(expectedCategory);
            assertThat(event.getThrowableProxy()).isNull();
            assertThat(event.getMDCPropertyMap()).isEmpty();
        }
    }

    private void awaitResponseGate() {
        var gate = responseGate;
        if (gate != null) {
            try {
                gate.await(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
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
        expectedCategory = body.contains("userId") ? "identity_mismatch" : "malformed_response";
        response = body;
        assertThatThrownBy(() -> client.load(ID)).isInstanceOf(EligibilityUnavailableException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"userId", "role", "verificationStatus", "authorizationExpiresAt"})
    void missingRequiredEligibilityInformationFailsClosed(String field) throws Exception {
        expectedCategory = "malformed_response";
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var body = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(VALID);
        body.remove(field);
        response = mapper.writeValueAsString(body);
        assertThatThrownBy(() -> client.load(ID)).isInstanceOf(EligibilityUnavailableException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "false", "\"\"", "\"invalid timestamp\""})
    void malformedExpiryMustNotBecomeUnlimited(String expiry) {
        expectedCategory = "malformed_response";
        response = VALID.replace("\"authorizationExpiresAt\":null", "\"authorizationExpiresAt\":" + expiry);
        assertThatThrownBy(() -> client.load(ID)).isInstanceOf(EligibilityUnavailableException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {302, 401, 403, 404, 500, 503})
    void dependencyHttpErrorsFailClosedWithoutLeakingResponse(int httpStatus) {
        expectedCategory = "dependency_http";
        status = httpStatus;
        response = "sentinel-sensitive-dependency-response";
        assertThatThrownBy(() -> client.load(ID)).isInstanceOf(EligibilityUnavailableException.class)
                .hasMessage("Authoritative inspector eligibility could not be validated");
    }

    @Test
    void connectionFailureFailsClosed() {
        expectedCategory = "transport";
        server.stop(0);
        assertThatThrownBy(() -> client.load(ID)).isInstanceOf(EligibilityUnavailableException.class);
    }

    @Test
    void responseHeaderTimeoutIsTransportFailure() {
        expectedCategory = "transport";
        responseGate = new java.util.concurrent.CountDownLatch(1);
        try {
            assertThatThrownBy(() -> client.load(ID)).isInstanceOf(EligibilityUnavailableException.class)
                    .hasMessage("Authoritative inspector eligibility could not be validated")
                    .hasNoCause();
        } finally {
            responseGate.countDown();
        }
    }

    @Test
    void interruptedResponseBodyIsTransportFailure() {
        expectedCategory = "transport";
        interruptBody = true;
        assertThatThrownBy(() -> client.load(ID)).isInstanceOf(EligibilityUnavailableException.class)
                .hasMessage("Authoritative inspector eligibility could not be validated").hasNoCause();
    }

    @Test
    void malformedSensitiveResponseDoesNotEscapeIntoDiagnostics() {
        expectedCategory = "malformed_response";
        response = "sentinel-private@example.test-test-service-secret";
        assertThatThrownBy(() -> client.load(ID)).isInstanceOf(EligibilityUnavailableException.class)
                .hasMessage("Authoritative inspector eligibility could not be validated").hasNoCause();
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/plain", "invalid-media-type"})
    void invalidContentTypeRemainsFailClosed(String type) {
        expectedCategory = "malformed_response";
        contentType = type;
        assertThatThrownBy(() -> client.load(ID)).isInstanceOf(EligibilityUnavailableException.class)
                .hasMessage("Authoritative inspector eligibility could not be validated").hasNoCause();
    }

    @Test
    void emptySecretIsRejected() {
        assertThatThrownBy(() -> new InternalUserEligibilityClient(RestClient.builder(),
                "http://localhost", " ", 500, 500)).isInstanceOf(IllegalArgumentException.class);
    }
}
