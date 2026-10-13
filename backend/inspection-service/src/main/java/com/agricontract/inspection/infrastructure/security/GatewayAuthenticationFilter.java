package com.agricontract.inspection.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

/** Frozen gateway §2: forwarded JWT identity is trusted only after gateway authentication. */
public class GatewayAuthenticationFilter extends OncePerRequestFilter {
    private static final Set<String> ROLES = Set.of("BUYER", "SELLER", "ADMIN", "OPERATOR", "INSPECTOR", "ARBITRATOR");
    private final byte[] gatewaySecret;

    public GatewayAuthenticationFilter(String gatewaySecret) {
        if (gatewaySecret == null || gatewaySecret.isBlank()) {
            throw new IllegalArgumentException("GATEWAY_INTERNAL_SECRET must be configured");
        }
        this.gatewaySecret = gatewaySecret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return (request.getContextPath() + "/actuator/health").equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain chain) throws ServletException, IOException {
        var existing = SecurityContextHolder.getContext().getAuthentication();
        if (existing != null && existing.isAuthenticated() && !(existing instanceof AnonymousAuthenticationToken)) {
            // Never replace an authoritative principal with request headers.
            chain.doFilter(request, response);
            return;
        }
        String secret = singleHeader(request, "X-Gateway-Secret");
        String actor = singleHeader(request, "X-User-Id");
        String roles = singleHeader(request, "X-User-Role");
        if (secret == null || !MessageDigest.isEqual(gatewaySecret, secret.getBytes(StandardCharsets.UTF_8))
                || actor == null || actor.isBlank() || roles == null || roles.isBlank()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        var authorities = Arrays.stream(roles.split(",")).map(String::trim)
                .filter(ROLES::contains).distinct().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(actor, null, authorities));
        SecurityContextHolder.setContext(context);
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private String singleHeader(HttpServletRequest request, String name) {
        var values = Collections.list(request.getHeaders(name));
        return values.size() == 1 ? values.getFirst() : null;
    }
}
