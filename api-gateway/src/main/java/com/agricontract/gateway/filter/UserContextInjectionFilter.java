package com.agricontract.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
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


                    String roles = "";
                    Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
                    if (realmAccess == null || !realmAccess.containsKey("roles")) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid token: Missing roles claim");
                    }

                    List<String> roleList = (List<String>) realmAccess.get("roles");
                    if (roleList == null || roleList.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid token: Roles list is empty");
                    }

                    roles = String.join(",", roleList);

                    ServerHttpRequest mutated = exchange.getRequest().mutate()
                            .header("X-User-Id", id)
                            .header("X-User-Email", email)
                            .header("X-User-Role", roles)
                            .headers(h -> h.remove(HttpHeaders.AUTHORIZATION))
                            .build();
                    return chain.filter(exchange.mutate().request(mutated).build());
                })
                .switchIfEmpty(Mono.defer(() -> {
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }));
    }
}
