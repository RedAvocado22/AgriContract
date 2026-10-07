package com.agricontract.user.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    static final String FORBIDDEN_BODY = "{\"success\":false,\"message\":\"Forbidden\",\"data\":null}";

    private final String gatewaySecret;
    private final String serviceInternalSecret;

    public HeaderAuthenticationFilter(String gatewaySecret, String serviceInternalSecret) {
        this.gatewaySecret = gatewaySecret;
        this.serviceInternalSecret = serviceInternalSecret;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/actuator/health") || path.equals("/actuator/info");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Service-to-service calls carry no end-user identity, so they never get role-based authorities.
        String internalSecret = request.getHeader("X-Internal-Secret");
        if (internalSecret != null && MessageDigest.isEqual(
                serviceInternalSecret.getBytes(StandardCharsets.UTF_8), internalSecret.getBytes(StandardCharsets.UTF_8))) {
            filterChain.doFilter(request, response);
            return;
        }

        String headerSecret = request.getHeader("X-Gateway-Secret");

        if (headerSecret == null || !MessageDigest.isEqual(
                headerSecret.getBytes(StandardCharsets.UTF_8), gatewaySecret.getBytes(StandardCharsets.UTF_8))) {
            writeForbidden(response);
            return;
        }

        // Identity headers are trusted only here: the gateway strips client-sent X-User-* and re-injects them from the JWT.
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.isBlank()) {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(
                    userId, null, toAuthorities(request.getHeader("X-User-Role"))));
            SecurityContextHolder.setContext(context);
        }

        filterChain.doFilter(request, response);
    }

    static void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write(FORBIDDEN_BODY);
    }

    private static List<GrantedAuthority> toAuthorities(String rawRoles) {
        if (rawRoles == null || rawRoles.isBlank()) {
            return List.of();
        }
        return Arrays.stream(rawRoles.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(Locale.ROOT)))
                .toList();
    }
}
