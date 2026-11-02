package com.agricontract.product.infrastructure.client;

import com.agricontract.product.application.dto.UserEligibility;
import com.agricontract.product.common.exception.EligibilityUnavailableException;
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
    private static final String ID = "9b0c8f0e-2f43-4a51-9d8c-1d6f3f1e7a10";
    private static final String VALID = """
            {"userId":"%s","organizationName":"HTX Ea Tu","email":"private@example.test","role":"SELLER",
             "verificationStatus":"VERIFIED","authorizationExpiresAt":"2027-06-30T16:59:59.999Z",
             "lockedUntil":null}
            """.formatted(ID);

    private HttpServer server;
    private InternalUserEligibilityClient client;
    private volatile int status = 200;
    private volatile String body = VALID;
    private volatile String contentType = "application/json";
    private volatile long delayMs;
    private final AtomicReference<String> requestedPath = new AtomicReference<>();
    private final AtomicReference<String> internalSecret = new AtomicReference<>();

    @BeforeEach
    void startUserServiceStub() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestedPath.set(exchange.getRequestURI().getPath());
            internalSecret.set(exchange.getRequestHeaders().getFirst("X-Internal-Secret"));
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        client = new InternalUserEligibilityClient(RestClient.builder(),
                "http://127.0.0.1:" + server.getAddress().getPort(), "test-service-secret", 500, 300);
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    @Test
    void load_readsTheInternalEndpointWithTheServiceSecret() {
        UserEligibility facts = client.load(ID).orElseThrow();

        assertThat(requestedPath.get()).isEqualTo("/internal/v1/users/" + ID);
        assertThat(internalSecret.get()).isEqualTo("test-service-secret");
        assertThat(facts).isEqualTo(new UserEligibility(ID, "VERIFIED",
                Instant.parse("2027-06-30T16:59:59.999Z"), null));
    }

    @Test
    void load_lockedUser_carriesLockedUntil() {
        body = VALID.replace("\"lockedUntil\":null", "\"lockedUntil\":\"2099-01-01T00:00:00Z\"");

        assertThat(client.load(ID).orElseThrow().lockedUntil()).isEqualTo(Instant.parse("2099-01-01T00:00:00Z"));
    }

    @Test
    void load_noProfile_isEmptyNotUnavailable() {
        status = 404;
        body = "{\"success\":false,\"message\":\"User not found\"}";

        assertThat(client.load(ID)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 500, 502, 503})
    void load_otherErrorStatus_failsClosed(int code) {
        status = code;
        assertThatThrownBy(() -> client.load(ID)).isInstanceOf(EligibilityUnavailableException.class);
    }

    @Test
    void load_userServiceDown_failsClosed() {
        server.stop(0);
        assertThatThrownBy(() -> client.load(ID)).isInstanceOf(EligibilityUnavailableException.class);
    }

    @Test
    void load_slowUserService_timesOutAndFailsClosed() {
        delayMs = 1_000;
        assertThatThrownBy(() -> client.load(ID)).isInstanceOf(EligibilityUnavailableException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not json",
            "[]",
            // a missing key must not read as "not locked" / "no expiry"
            "{\"userId\":\"" + ID + "\",\"verificationStatus\":\"VERIFIED\",\"authorizationExpiresAt\":null}",
            "{\"userId\":\"" + ID + "\",\"verificationStatus\":\"VERIFIED\",\"lockedUntil\":null}",
            "{\"userId\":\"" + ID + "\",\"authorizationExpiresAt\":null,\"lockedUntil\":null}",
            "{\"userId\":\"" + ID + "\",\"verificationStatus\":\"VERIFIED\",\"authorizationExpiresAt\":null,"
                    + "\"lockedUntil\":\"tomorrow\"}",
            "{\"userId\":\"someone-else\",\"verificationStatus\":\"VERIFIED\",\"authorizationExpiresAt\":null,"
                    + "\"lockedUntil\":null}"
    })
    void load_malformedOrMismatchedBody_failsClosed(String malformed) {
        body = malformed;
        assertThatThrownBy(() -> client.load(ID)).isInstanceOf(EligibilityUnavailableException.class);
    }

    @Test
    void load_nonJsonContentType_failsClosed() {
        contentType = "text/html";
        assertThatThrownBy(() -> client.load(ID)).isInstanceOf(EligibilityUnavailableException.class);
    }

    @Test
    void constructor_blankSecret_isRejected() {
        assertThatThrownBy(() -> new InternalUserEligibilityClient(RestClient.builder(), "http://127.0.0.1:1", " ",
                500, 500)).isInstanceOf(IllegalArgumentException.class);
    }
}
