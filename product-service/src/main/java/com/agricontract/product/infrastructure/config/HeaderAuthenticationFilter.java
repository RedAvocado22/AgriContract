package com.agricontract.product.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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

    private boolean isPublicRead(HttpServletRequest request) {
        if (!HttpMethod.GET.matches(request.getMethod())) {
            return false;
        }

        String path = request.getRequestURI();
        return path.startsWith("/api/v1/listings") || path.startsWith("/api/v1/products");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (isPublicRead(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String internalSecret = request.getHeader("X-Internal-Secret");
        if (serviceInternalSecret.equals(internalSecret)) {
            filterChain.doFilter(request, response);
            return;
        }

        String headerSecret = request.getHeader("X-Gateway-Secret");

        if (headerSecret == null || !headerSecret.equals(gatewaySecret)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"success\":false,\"message\":\"Forbidden\",\"data\":null}"
            );
            return;
        }

        String userId = request.getHeader("X-User-Id");
        String userRole = request.getHeader("X-User-Role");

        if (userId != null && userRole != null) {
            List<SimpleGrantedAuthority> authorities = Arrays.stream(userRole.split(","))
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.trim().toUpperCase()))
                    .collect(Collectors.toList());
            var auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }
}
