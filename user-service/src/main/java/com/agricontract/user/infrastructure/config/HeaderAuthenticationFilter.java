package com.agricontract.user.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class HeaderAuthenticationFilter extends OncePerRequestFilter {

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

        String internalSecret = request.getHeader("X-Internal-Secret");
        if (internalSecret != null && MessageDigest.isEqual(
                serviceInternalSecret.getBytes(StandardCharsets.UTF_8), internalSecret.getBytes(StandardCharsets.UTF_8))) {
            filterChain.doFilter(request, response);
            return;
        }

        String headerSecret = request.getHeader("X-Gateway-Secret");

        if (headerSecret == null || !MessageDigest.isEqual(
                headerSecret.getBytes(StandardCharsets.UTF_8), gatewaySecret.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"success\":false,\"message\":\"Forbidden\",\"data\":null}"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }
}