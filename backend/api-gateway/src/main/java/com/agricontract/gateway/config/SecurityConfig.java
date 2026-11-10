package com.agricontract.gateway.config;

import com.agricontract.gateway.infrastructure.security.KeycloakRealmRoleConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    // Public detail routes only match real identifiers, so static siblings such as /listings/seller
    // or /products/mine fall through to the authenticated default instead of being exposed anonymously.
    private static final String UUID = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    private static final String COMMODITY = "[A-Z][A-Z_]*";

    /**
     * /internal/** is answered with 404 before any bearer token is parsed: this chain has no resource
     * server, so a missing, invalid or valid token all get the same response and nothing is forwarded.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityWebFilterChain internalRouteFilterChain(ServerHttpSecurity http) {
        return http
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers("/internal", "/internal/**"))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges.anyExchange().denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((exchange, ex) -> notFound(exchange))
                        .accessDeniedHandler((exchange, ex) -> notFound(exchange))
                )
                .build();
    }

    /**
     * Callers that never carry a user JWT (gateway design 3.1 and 3.4): the External Verifier lock/unlock routes,
     * authenticated by the ES256 signature in the body at bank-service, and audit-hash, authenticated by
     * X-Api-Key at audit-service. No resource server here, so a stray Authorization header is neither
     * validated nor forwarded, and no X-User-* identity is injected. Any other method on these paths is 404.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    public SecurityWebFilterChain externalCallerFilterChain(ServerHttpSecurity http) {
        return http
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers(
                        "/api/v1/security/emergency-lock",
                        "/api/v1/security/emergency-unlock",
                        "/api/v1/security/audit-hash"))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.POST,
                                "/api/v1/security/emergency-lock",
                                "/api/v1/security/emergency-unlock").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/v1/security/audit-hash").permitAll()
                        .anyExchange().denyAll()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((exchange, ex) -> notFound(exchange))
                        .accessDeniedHandler((exchange, ex) -> notFound(exchange))
                )
                .build();
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(Customizer.withDefaults())
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/actuator/health", "/actuator/info").permitAll()
                        .pathMatchers(HttpMethod.GET,
                                "/api/v1/listings",
                                "/api/v1/listings/{listingId:" + UUID + "}",
                                "/api/v1/products/{productId:" + UUID + "}",
                                // Reference prices; the upper-case commodity keeps /prices/manual off the public rule.
                                "/api/v1/prices/{commodity:" + COMMODITY + "}/{province}",
                                "/api/v1/prices/{commodity:" + COMMODITY + "}/{province}/items").permitAll()
                        // Coarse staff gate (gateway design 3.3, SRS v1.6): each action takes exactly its own staff
                        // role, no ADMIN fallback. Exact method+path matchers sit before the wildcards they narrow;
                        // the owning service still re-checks role and resource.
                        .pathMatchers(HttpMethod.GET, "/api/v1/admin/users", "/api/v1/admin/users/*").hasRole("OPERATOR")
                        .pathMatchers(HttpMethod.POST,
                                "/api/v1/admin/users/*/verify",
                                "/api/v1/admin/users/*/reject").hasRole("OPERATOR")
                        .pathMatchers("/api/v1/admin/users", "/api/v1/admin/users/**").hasRole("ADMIN")
                        // INSPECTOR also decides breach cases (decisionSource INSPECTOR_L1_5/L2, OpenAPI x-roles).
                        .pathMatchers(HttpMethod.POST,
                                "/api/v1/contracts/*/breach-cases/*/review",
                                "/api/v1/contracts/*/breach-cases/*/resolve").hasAnyRole("ARBITRATOR", "INSPECTOR")
                        .pathMatchers(HttpMethod.POST,
                                "/api/v1/contracts/*/milestones/*/force-majeure/resolve").hasRole("ARBITRATOR")
                        .pathMatchers(HttpMethod.POST,
                                "/api/v1/contracts/*/activation/retry-deposit-lock",
                                "/api/v1/contracts/*/milestones/*/funding/retry").hasRole("OPERATOR")
                        // #233: only a party withdraws an offer before the second signature.
                        .pathMatchers(HttpMethod.POST, "/api/v1/contracts/*/withdraw").hasAnyRole("BUYER", "SELLER")
                        // D42: the parties, not OPERATOR, stop a contract whose deposit lock failed.
                        .pathMatchers("/api/v1/contracts/*/activation/cancel",
                                "/api/v1/contracts/*/activation/cancel/**").hasAnyRole("BUYER", "SELLER")
                        .pathMatchers("/api/v1/admin/categories/**", "/api/v1/admin/products/**").hasRole("OPERATOR")
                        .pathMatchers(HttpMethod.POST, "/api/v1/admin/reputation/actions/propose").hasRole("OPERATOR")
                        .pathMatchers(HttpMethod.POST,
                                "/api/v1/admin/reputation/actions/*/approve",
                                "/api/v1/admin/reputation/actions/*/reject").hasRole("ARBITRATOR")
                        .pathMatchers("/api/v1/admin/reputation/**").hasAnyRole("OPERATOR", "ARBITRATOR")
                        .pathMatchers(HttpMethod.POST, "/api/v1/inspections/*/milestones/*/reports").hasRole("INSPECTOR")
                        .pathMatchers("/api/v1/inspector/**").hasRole("INSPECTOR")
                        .pathMatchers("/api/v1/admin/inspections/**").hasRole("OPERATOR")
                        .pathMatchers(HttpMethod.POST, "/api/v1/prices/manual").hasRole("OPERATOR")
                        .pathMatchers("/api/v1/admin/prices/**").hasRole("OPERATOR")
                        .pathMatchers("/api/v1/analytics/**", "/api/v1/admin/analytics/**").hasRole("ADMIN")
                        .pathMatchers("/api/v1/admin/security/**", "/api/v1/admin/audit/**").hasRole("ADMIN")
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(
                        oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                                .bearerTokenConverter(exchange -> {
                                    String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
                                    if (auth == null || !auth.startsWith("Bearer ")) {
                                        return Mono.empty();
                                    }
                                    return new ServerBearerTokenAuthenticationConverter().convert(exchange);
                                })
                )
                .build();
    }

    private static ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }

    private static Mono<Void> notFound(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
        return exchange.getResponse().setComplete();
    }
}
