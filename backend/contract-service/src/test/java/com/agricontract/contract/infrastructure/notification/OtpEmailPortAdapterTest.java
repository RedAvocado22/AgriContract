package com.agricontract.contract.infrastructure.notification;

import com.agricontract.contract.application.dto.OtpEmail;
import com.agricontract.contract.application.exception.OtpEmailNotSentException;
import com.agricontract.contract.application.port.OtpEmailPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import feign.Request;
import feign.RetryableException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real Feign wiring against a stub notification-service: request shape, secret, timeouts, no retry, error codes. */
@SpringBootTest(classes = OtpEmailPortAdapterTest.FeignTestConfig.class,
        properties = "service.internal-secret=test-internal-secret")
class OtpEmailPortAdapterTest {
    private static final OtpEmail EMAIL = new OtpEmail("6f1c2a52-0d8e-4b55-9d0c-6b2e7c3f9a10", "seller-1",
            "seller@agricontract.test", "c-1", "123456", "a".repeat(64), Instant.parse("2026-11-08T10:05:00Z"));

    private static final AtomicInteger calls = new AtomicInteger();
    private static final AtomicReference<String> lastPath = new AtomicReference<>();
    private static final AtomicReference<String> lastSecret = new AtomicReference<>();
    private static final AtomicReference<String> lastBody = new AtomicReference<>();
    private static final AtomicReference<Reply> reply = new AtomicReference<>();
    private static final HttpServer server = startServer();

    record Reply(int status, String body, long delayMs) {
    }

    @Configuration
    @ImportAutoConfiguration({JacksonAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class,
            FeignAutoConfiguration.class})
    @EnableFeignClients(clients = NotificationServiceClient.class)
    @Import(OtpEmailPortAdapter.class)
    static class FeignTestConfig {
    }

    @Autowired OtpEmailPort port;
    @Autowired ObjectMapper objectMapper;

    @DynamicPropertySource
    static void notificationUrl(DynamicPropertyRegistry registry) {
        registry.add("notification-service.url", () -> "http://localhost:" + server.getAddress().getPort());
    }

    @BeforeEach
    void reset() {
        calls.set(0);
        lastPath.set(null);
        lastSecret.set(null);
        lastBody.set(null);
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @Test
    void accepted_postsExactlyTheOtpEmailRequestWithTheSecret() throws Exception {
        reply.set(new Reply(200, "", 0));

        port.send(EMAIL);

        assertThat(lastPath.get()).isEqualTo("/internal/v1/notifications/otp-email");
        assertThat(lastSecret.get()).isEqualTo("test-internal-secret");
        JsonNode body = objectMapper.readTree(lastBody.get());
        Set<String> keys = StreamSupport.stream(((Iterable<String>) body::fieldNames).spliterator(), false)
                .collect(Collectors.toSet());
        assertThat(keys).containsExactlyInAnyOrder("requestId", "recipientUserId", "recipientEmail", "contractId",
                "otpCode", "signedContentHash", "expiresAt");
        assertThat(body.get("requestId").asText()).isEqualTo(EMAIL.requestId());
        assertThat(body.get("recipientEmail").asText()).isEqualTo("seller@agricontract.test");
        assertThat(body.get("otpCode").asText()).isEqualTo("123456");
        assertThat(body.get("expiresAt").asText()).isEqualTo("2026-11-08T10:05:00Z");
    }

    @Test
    void providerFailure_isNotSent_withTheEnvelopeCode_andIsNotRetried() {
        reply.set(new Reply(502, "{\"status\":502,\"code\":\"OTP_EMAIL_PROVIDER_FAILED\",\"message\":\"x\"}", 0));

        assertThatThrownBy(() -> port.send(EMAIL))
                .isInstanceOf(OtpEmailNotSentException.class)
                .extracting("code").isEqualTo("OTP_EMAIL_PROVIDER_FAILED");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void inProgressOrLogUnavailable_isNotSent() {
        reply.set(new Reply(503, "{\"code\":\"OTP_EMAIL_IN_PROGRESS\"}", 0));

        assertThatThrownBy(() -> port.send(EMAIL))
                .isInstanceOf(OtpEmailNotSentException.class)
                .extracting("code").isEqualTo("OTP_EMAIL_IN_PROGRESS");
    }

    @Test
    void serverErrorWithoutEnvelope_isNotSent() {
        reply.set(new Reply(500, "<html>boom</html>", 0));

        assertThatThrownBy(() -> port.send(EMAIL))
                .isInstanceOf(OtpEmailNotSentException.class)
                .extracting("code").isEqualTo(OtpEmailNotSentException.UNKNOWN);
    }

    @Test
    void slowAnswer_waitsTheFullFiveSeconds_thenIsNotSent() {
        reply.set(new Reply(200, "", 6500));
        long started = System.nanoTime();

        assertThatThrownBy(() -> port.send(EMAIL))
                .isInstanceOf(OtpEmailNotSentException.class)
                .extracting("code").isEqualTo(OtpEmailNotSentException.TIMEOUT);
        assertThat((System.nanoTime() - started) / 1_000_000).isGreaterThanOrEqualTo(5000);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void configuredTimeouts_readAtLeastFiveSeconds() {
        var options = new NotificationFeignConfig().notificationTimeouts();
        assertThat(options.readTimeoutMillis()).isGreaterThanOrEqualTo(5000);
    }

    @Test
    void nobodyListening_isNotSent() {
        Request request = Request.create(Request.HttpMethod.POST, "http://notification", Map.of(), null,
                StandardCharsets.UTF_8, null);
        NotificationServiceClient dead = body -> {
            throw new RetryableException(-1, "Connection refused", Request.HttpMethod.POST,
                    new ConnectException("Connection refused"), (Long) null, request);
        };
        OtpEmailPortAdapter adapter = new OtpEmailPortAdapter(dead, objectMapper);

        assertThatThrownBy(() -> adapter.send(EMAIL))
                .isInstanceOf(OtpEmailNotSentException.class)
                .extracting("code").isEqualTo(OtpEmailNotSentException.UNREACHABLE);
    }

    private static HttpServer startServer() {
        try {
            HttpServer httpServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            httpServer.setExecutor(Executors.newCachedThreadPool());
            httpServer.createContext("/", exchange -> {
                calls.incrementAndGet();
                lastPath.set(exchange.getRequestURI().getPath());
                lastSecret.set(exchange.getRequestHeaders().getFirst("X-Internal-Secret"));
                lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                Reply r = reply.get();
                try {
                    Thread.sleep(r.delayMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                byte[] bytes = r.body().getBytes(StandardCharsets.UTF_8);
                try {
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(r.status(), bytes.length == 0 ? -1 : bytes.length);
                    if (bytes.length > 0) {
                        exchange.getResponseBody().write(bytes);
                    }
                } catch (IOException clientGone) {
                    // the caller timed out first
                } finally {
                    exchange.close();
                }
            });
            httpServer.start();
            return httpServer;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

}
