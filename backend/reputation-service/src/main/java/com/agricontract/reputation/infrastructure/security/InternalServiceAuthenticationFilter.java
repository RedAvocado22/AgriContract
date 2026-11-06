package com.agricontract.reputation.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Used only by the internal security chain, never alongside GatewayIdentityFilter. */
public class InternalServiceAuthenticationFilter extends OncePerRequestFilter {
    private final byte[] secret;

    public InternalServiceAuthenticationFilter(String configuredSecret) {
        secret = configuredSecret == null || configuredSecret.isBlank()
                ? null : configuredSecret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        SecurityContextHolder.clearContext();
        try {
            var values = Collections.list(request.getHeaders("X-Internal-Secret"));
            if (secret != null && values.size() == 1
                    && MessageDigest.isEqual(secret, values.getFirst().getBytes(StandardCharsets.UTF_8))) {
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        "internal-service", null, List.of(new SimpleGrantedAuthority("SERVICE_INTERNAL"))));
                SecurityContextHolder.setContext(context);
            }
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
