package com.agricontract.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Component
public class UserContextInjectionFilter implements GlobalFilter {

    @Value("${gateway.internal-secret}")
    private String internalSecret;

    List<String> PUBLIC_PATHS = List.of(
            "/api/v1/listings",
            "/api/v1/products"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();

        boolean isPublic = HttpMethod.GET.equals(method) && PUBLIC_PATHS.stream().anyMatch(path::startsWith);

        if (isPublic) {
            return chain.filter(exchange);
        }

        return exchange.getPrincipal()
                .filter(p -> p instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .flatMap(jwtAuth -> {
                    Jwt jwt = jwtAuth.getToken();

                    String id = jwt.getSubject();
                    if (id == null || id.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token: Missing subject");
                    }

                    String email = jwt.getClaimAsString("email");
                    if (email == null || email.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token: Missing subject");
                    }


                    Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
                    if (realmAccess == null || !realmAccess.containsKey("roles")) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid token: Missing roles claim");
                    }

                    List<String> roleList = (List<String>) realmAccess.get("roles");
                    if (roleList == null || roleList.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid token: Roles list is empty");
                    }

                    String roles = String.join(",", roleList);

                    ServerHttpRequest decorated = new UserContextRequestDecorator(
                            exchange.getRequest(), id, email, roles, internalSecret);
                    return chain.filter(exchange.mutate().request(decorated).build());
                })
                .switchIfEmpty(Mono.defer(() -> {
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }));
    }

    /**
     * Original request's HttpHeaders is read-only by contract (AbstractServerHttpRequest
     * wraps it via HttpHeaders.readOnlyHttpHeaders). Building a brand-new HttpHeaders here
     * (instead of relying on ServerHttpRequest.Builder#header(...)) sidesteps that entirely.
     */
    private static class UserContextRequestDecorator extends ServerHttpRequestDecorator {

        private final HttpHeaders headers;

        UserContextRequestDecorator(ServerHttpRequest delegate, String userId, String email,
                                     String roles, String gatewaySecret) {
            super(delegate);
            HttpHeaders copy = new HttpHeaders();
            copy.addAll(delegate.getHeaders());
            copy.remove(HttpHeaders.AUTHORIZATION);
            copy.set("X-User-Id", userId);
            copy.set("X-User-Email", email);
            copy.set("X-User-Role", roles);
            copy.set("X-Gateway-Secret", gatewaySecret);
            this.headers = HttpHeaders.readOnlyHttpHeaders(copy);
        }

        @Override
        public HttpHeaders getHeaders() {
            return this.headers;
        }
    }
}
