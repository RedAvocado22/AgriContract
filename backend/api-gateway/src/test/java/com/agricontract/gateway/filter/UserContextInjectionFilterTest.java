package com.agricontract.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class UserContextInjectionFilterTest {

    private static final String SECRET = "test-gateway-secret";

    private UserContextInjectionFilter filter;
    private AtomicReference<ServerWebExchange> forwarded;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new UserContextInjectionFilter();
        ReflectionTestUtils.setField(filter, "internalSecret", SECRET);
        forwarded = new AtomicReference<>();
        chain = exchange -> {
            forwarded.set(exchange);
            return Mono.empty();
        };
    }

    @Test
    void authenticatedRequest_forgedIdentityHeaders_areReplacedByJwtIdentity() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                .header("X-User-Id", "attacker")
                .header("X-User-Role", "ADMIN")
                .header("X-User-Email", "attacker@evil.test")
                .header("X-Gateway-Secret", "forged")
                .build();

        filter.filter(withPrincipal(request, jwt("user-123", "user@agri.test", List.of("BUYER"))), chain).block();

        HttpHeaders headers = forwardedHeaders();
        assertThat(headers.get("X-User-Id")).containsExactly("user-123");
        assertThat(headers.get("X-User-Role")).containsExactly("BUYER");
        assertThat(headers.get("X-User-Email")).containsExactly("user@agri.test");
        assertThat(headers.get("X-Gateway-Secret")).containsExactly(SECRET);
        assertThat(headers.containsKey(HttpHeaders.AUTHORIZATION)).isFalse();
    }

    @Test
    void anonymousRequest_forgedIdentityHeaders_areStrippedAndGatewaySecretInjected() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/listings")
                .header("X-User-Id", "attacker")
                .header("X-User-Role", "ADMIN")
                .header("X-Gateway-Secret", "forged")
                .build();

        filter.filter(MockServerWebExchange.from(request), chain).block();

        HttpHeaders headers = forwardedHeaders();
        assertThat(headers.containsKey("X-User-Id")).isFalse();
        assertThat(headers.containsKey("X-User-Role")).isFalse();
        assertThat(headers.get("X-Gateway-Secret")).containsExactly(SECRET);
    }

    @Test
    void anyXUserHeaderAndInternalSecret_areStrippedCaseInsensitively() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/listings")
                .header("x-user-tenant", "other-tenant")
                .header("X-USER-ID", "attacker")
                .header("X-Internal-Secret", "guessed")
                .header("X-Correlation-Id", "keep-me")
                .build();

        filter.filter(MockServerWebExchange.from(request), chain).block();

        HttpHeaders headers = forwardedHeaders();
        assertThat(headers.keySet()).noneMatch(name -> name.toLowerCase(Locale.ROOT).startsWith("x-user-"));
        assertThat(headers.containsKey("X-Internal-Secret")).isFalse();
        assertThat(headers.get("X-Correlation-Id")).containsExactly("keep-me");
    }

    @Test
    void anonymousRequest_rawAuthorizationHeader_isNotForwarded() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/listings")
                .header(HttpHeaders.AUTHORIZATION, "bearer not-parsed-by-security")
                .build();

        filter.filter(MockServerWebExchange.from(request), chain).block();

        assertThat(forwardedHeaders().containsKey(HttpHeaders.AUTHORIZATION)).isFalse();
    }

    @Test
    void jwtWithoutEmail_isRejectedWithUnauthorized() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users/me").build();

        StepVerifier.create(filter.filter(withPrincipal(request, jwt("user-123", null, List.of("BUYER"))), chain))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ResponseStatusException.class);
                    assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                })
                .verify();
        assertThat(forwarded.get()).isNull();
    }

    @Test
    void jwtWithoutRoles_isRejectedWithForbidden() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users/me").build();

        StepVerifier.create(filter.filter(withPrincipal(request, jwt("user-123", "user@agri.test", List.of())), chain))
                .expectErrorSatisfies(error ->
                        assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN))
                .verify();
        assertThat(forwarded.get()).isNull();
    }

    private HttpHeaders forwardedHeaders() {
        assertThat(forwarded.get()).as("request was forwarded").isNotNull();
        return forwarded.get().getRequest().getHeaders();
    }

    private static ServerWebExchange withPrincipal(MockServerHttpRequest request, Jwt jwt) {
        return MockServerWebExchange.from(request).mutate()
                .principal(Mono.just(new JwtAuthenticationToken(jwt)))
                .build();
    }

    private static Jwt jwt(String subject, String email, List<String> roles) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .claim("realm_access", Map.of("roles", roles));
        if (email != null) {
            builder.claim("email", email);
        }
        return builder.build();
    }
}
