package com.agricontract.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.context.SecurityContextServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class UserContextInjectionFilterTest {

    private UserContextInjectionFilter filter;

    @BeforeEach
    void setUp() {
        filter = new UserContextInjectionFilter();
        ReflectionTestUtils.setField(filter, "internalSecret", "gateway-secret");
    }

    @Test
    void publicListingDetail_doesNotRequireAuthentication() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/listings/listing-1").build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, capture(forwarded)).block();

        assertThat(forwarded.get()).isSameAs(exchange);
    }

    @Test
    void sellerListings_withoutAuthentication_returnsUnauthorized() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/listings/seller").build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, capture(forwarded)).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(forwarded.get()).isNull();
    }

    @Test
    void sellerListings_withAuthentication_injectsUserContext() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("seller-1")
                .claim("email", "seller@test.com")
                .claim("realm_access", Map.of("roles", List.of("SELLER")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        MockServerWebExchange delegate = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/listings/seller").build());
        ServerWebExchange exchange = new SecurityContextServerWebExchange(
                delegate, Mono.just(new SecurityContextImpl(new JwtAuthenticationToken(jwt))));
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, capture(forwarded)).block();

        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("seller-1");
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-User-Role")).isEqualTo("SELLER");
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-Gateway-Secret")).isEqualTo("gateway-secret");
    }

    private GatewayFilterChain capture(AtomicReference<ServerWebExchange> forwarded) {
        return exchange -> {
            forwarded.set(exchange);
            return Mono.empty();
        };
    }
}
