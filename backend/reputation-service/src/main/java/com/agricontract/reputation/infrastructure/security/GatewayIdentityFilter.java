package com.agricontract.reputation.infrastructure.security;
import com.agricontract.reputation.domain.model.FactValues;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class GatewayIdentityFilter extends OncePerRequestFilter {
    private static final Set<String> ROLES = Set.of("BUYER", "SELLER", "INSPECTOR", "OPERATOR", "ARBITRATOR", "ADMIN");
    private final byte[] secret;
    public GatewayIdentityFilter(String configuredSecret) {
        if (configuredSecret == null || configuredSecret.isBlank()) throw new IllegalArgumentException("Gateway secret required");
        secret = configuredSecret.getBytes(StandardCharsets.UTF_8);
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        SecurityContextHolder.clearContext();
        try {
            String supplied = single(request, "X-Gateway-Secret");
            String id = single(request, "X-User-Id");
            String roles = single(request, "X-User-Role");
            if (supplied != null && MessageDigest.isEqual(secret, supplied.getBytes(StandardCharsets.UTF_8))
                    && id != null && roles != null) {
                try {
                    var user = FactValues.uuid(id);
                    var roleList = Arrays.stream(roles.split(",", -1)).map(String::trim).toList();
                    if (!roleList.isEmpty() && ROLES.containsAll(roleList)) {
                        var authorities = roleList.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
                        var context = SecurityContextHolder.createEmptyContext();
                        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, authorities));
                        SecurityContextHolder.setContext(context);
                    }
                } catch (IllegalArgumentException ignored) { /* No trusted identity. Authorization rejects the request. */ }
            }
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
    private static String single(HttpServletRequest request, String name) {
        var values = Collections.list(request.getHeaders(name));
        return values.size() == 1 ? values.getFirst() : null;
    }
}
