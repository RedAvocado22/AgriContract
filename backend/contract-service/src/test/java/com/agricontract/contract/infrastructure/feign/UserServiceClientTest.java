package com.agricontract.contract.infrastructure.feign;

import com.agricontract.contract.application.dto.UserInfo;
import com.sun.net.httpserver.HttpServer;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

// Real Feign wiring against a stub user-service: checks the path, the secret header and that the
// bare internal body (with fields contract-service does not use) maps to UserInfo.
@SpringBootTest(classes = UserServiceClientTest.FeignTestConfig.class,
        properties = "service.internal-secret=test-internal-secret")
class UserServiceClientTest {

    private static final String INTERNAL_BODY = """
            {"userId":"buyer-1","organizationName":"Buyer Corp","email":"buyer@agricontract.test",
             "role":"BUYER","verificationStatus":"VERIFIED","authorizationExpiresAt":null,"lockedUntil":null}
            """;

    private static final AtomicReference<String> lastPath = new AtomicReference<>();
    private static final AtomicReference<String> lastSecret = new AtomicReference<>();
    private static final HttpServer server = startServer();

    @Configuration
    @ImportAutoConfiguration({JacksonAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class,
            FeignAutoConfiguration.class})
    @EnableFeignClients(clients = UserServiceClient.class)
    static class FeignTestConfig {
    }

    @Autowired
    private UserServiceClient userServiceClient;

    @DynamicPropertySource
    static void userServiceUrl(DynamicPropertyRegistry registry) {
        registry.add("user-service.url", () -> "http://localhost:" + server.getAddress().getPort());
    }

    @BeforeEach
    void reset() {
        lastPath.set(null);
        lastSecret.set(null);
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @Test
    void getUser_callsInternalEndpointWithSecret_andMapsBareBody() {
        UserInfo user = userServiceClient.getUser("buyer-1");

        assertThat(lastPath.get()).isEqualTo("/internal/v1/users/buyer-1");
        assertThat(lastSecret.get()).isEqualTo("test-internal-secret");
        assertThat(user).isEqualTo(new UserInfo("buyer-1", "Buyer Corp", "buyer@agricontract.test", "BUYER"));
    }

    private static HttpServer startServer() {
        try {
            HttpServer httpServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            httpServer.createContext("/", exchange -> {
                lastPath.set(exchange.getRequestURI().getPath());
                lastSecret.set(exchange.getRequestHeaders().getFirst("X-Internal-Secret"));
                byte[] body = INTERNAL_BODY.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            httpServer.start();
            return httpServer;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
