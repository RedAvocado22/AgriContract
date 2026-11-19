package com.agricontract.contract.infrastructure.feign;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agricontract.contract.application.exception.ListingDataInvalidException;
import com.agricontract.contract.application.exception.ListingServiceUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import feign.Feign;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #430 with the real Feign decoder against a stub product-service: a 200 whose body breaks the frozen schema is an
 * internal error (500) naming the listing and the field; no answer or a 5xx stays "unavailable" (503).
 */
@SpringBootTest(classes = ListingPortAdapterFeignTest.FeignTestConfig.class,
        properties = "service.internal-secret=test-internal-secret")
class ListingPortAdapterFeignTest {
    private static final String LISTING = "11111111-1111-1111-1111-111111111111";
    private static final String CONTRACT = "8a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d";

    // The 10/11 E2E seed: a category id that is not a UUID.
    private static final String SNAPSHOT_WITH_BAD_CATEGORY = """
            {"listingId":"%s","productId":"22222222-2222-2222-2222-222222222222","categoryId":"cat-seed-coffee"}
            """.formatted(LISTING);
    private static final String RESERVATION_WITH_BAD_QUANTITY = """
            {"contractId":"%s","listingId":"%s","reservedQuantity":"a lot","state":"RESERVED"}
            """.formatted(CONTRACT, LISTING);

    private static final AtomicInteger status = new AtomicInteger(200);
    private static final AtomicReference<String> body = new AtomicReference<>("{}");
    private static final HttpServer server = startServer();

    @Configuration
    @ImportAutoConfiguration({JacksonAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class,
            FeignAutoConfiguration.class})
    @EnableFeignClients(clients = ProductServiceClient.class)
    @Import(ListingPortAdapter.class)
    static class FeignTestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Autowired
    private ListingPortAdapter adapter;

    @DynamicPropertySource
    static void productServiceUrl(DynamicPropertyRegistry registry) {
        registry.add("product-service.url", () -> "http://localhost:" + server.getAddress().getPort());
    }

    @BeforeEach
    void reset() {
        status.set(200);
        body.set("{}");
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @Test
    void snapshotWithANonUuidCategory_isInvalidData_namingTheListingAndTheField() {
        body.set(SNAPSHOT_WITH_BAD_CATEGORY);

        assertThatThrownBy(() -> adapter.contractSnapshot(LISTING, "t"))
                .isInstanceOf(ListingDataInvalidException.class)
                .hasMessageContaining(LISTING)
                .hasMessageContaining("categoryId");
    }

    @Test
    void reservationWithAnUnreadableQuantity_isInvalidData() {
        body.set(RESERVATION_WITH_BAD_QUANTITY);

        assertThatThrownBy(() -> adapter.reserve(CONTRACT, LISTING, new BigDecimal("2500.5"), "t"))
                .isInstanceOf(ListingDataInvalidException.class)
                .hasMessageContaining(LISTING)
                .hasMessageContaining("reservedQuantity");
    }

    @Test
    void productServiceAnswering503_staysUnavailable() {
        status.set(503);

        assertThatThrownBy(() -> adapter.contractSnapshot(LISTING, "t"))
                .isInstanceOf(ListingServiceUnavailableException.class);
    }

    @Test
    void productServiceUnreachable_staysUnavailable() {
        HttpServer down = startServer();
        int port = down.getAddress().getPort();
        down.stop(0);
        ListingPortAdapter unreachable = new ListingPortAdapter(Feign.builder()
                .contract(new SpringMvcContract())
                .target(ProductServiceClient.class, "http://localhost:" + port), new ObjectMapper());

        assertThatThrownBy(() -> unreachable.contractSnapshot(LISTING, "t"))
                .isInstanceOf(ListingServiceUnavailableException.class);
    }

    private static HttpServer startServer() {
        try {
            HttpServer httpServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            httpServer.createContext("/", exchange -> {
                byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status.get(), bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            httpServer.start();
            return httpServer;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
