package com.agricontract.gateway.filter;

import com.agricontract.gateway.infrastructure.security.KeycloakRealmRoleConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;

/**
 * Strips client-supplied identity headers from every routed request and re-injects identity from the
 * verified JWT. Which paths require a JWT is decided by SecurityConfig: a request reaching this filter
 * without a principal is on a public route and is forwarded without X-User-* headers.
 */
@Component
public class UserContextInjectionFilter implements GlobalFilter {

    @Value("${gateway.internal-secret}")
    private String internalSecret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        return exchange.getPrincipal()
                .filter(p -> p instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(jwtAuth -> {
                    Jwt jwt = jwtAuth.getToken();

                    String id = jwt.getSubject();
                    if (id == null || id.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token: Missing subject");
                    }

                    String email = jwt.getClaimAsString("email");
                    if (email == null || email.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token: Missing email");
                    }

                    // Authorities hold only application roles (KeycloakRealmRoleConverter); realm housekeeping roles are dropped.
                    List<String> roleList = jwtAuth.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .filter(authority -> authority.startsWith(KeycloakRealmRoleConverter.ROLE_PREFIX))
                            .map(authority -> authority.substring(KeycloakRealmRoleConverter.ROLE_PREFIX.length()))
                            .toList();
                    if (roleList.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid token: No application role");
                    }

                    String roles = String.join(",", roleList);

                    return (ServerHttpRequest) new UserContextRequestDecorator(request, id, email, roles, internalSecret);
                })
                .defaultIfEmpty(new UserContextRequestDecorator(request, null, null, null, internalSecret))
                .flatMap(decorated -> chain.filter(exchange.mutate().request(decorated).build()));
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
            copy.remove("X-Internal-Secret");
            List<String> forgedUserHeaders = copy.keySet().stream()
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith("x-user-"))
                    .toList();
            forgedUserHeaders.forEach(copy::remove);
            if (userId != null) {
                copy.set("X-User-Id", userId);
                copy.set("X-User-Email", email);
                copy.set("X-User-Role", roles);
            }
            copy.set("X-Gateway-Secret", gatewaySecret);
            this.headers = HttpHeaders.readOnlyHttpHeaders(copy);
        }

        @Override
        public HttpHeaders getHeaders() {
            return this.headers;
        }
    }
}
