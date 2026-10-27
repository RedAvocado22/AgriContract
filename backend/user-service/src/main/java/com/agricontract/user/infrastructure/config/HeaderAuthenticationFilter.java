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
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    static final String FORBIDDEN_BODY = "{\"success\":false,\"message\":\"Forbidden\",\"data\":null}";
    static final String UNAUTHORIZED_BODY = "{\"success\":false,\"message\":\"Unauthorized\",\"data\":null}";

    private static final List<String> IDENTITY_HEADERS = List.of("X-User-Id", "X-User-Role", "X-User-Email");
    private static final GrantedAuthority OPERATOR = new SimpleGrantedAuthority("ROLE_OPERATOR");
    private static final GrantedAuthority ARBITRATOR = new SimpleGrantedAuthority("ROLE_ARBITRATOR");

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
        if (serviceInternalSecret != null && !serviceInternalSecret.isBlank()
                && internalSecret != null && !hasMultipleValues(request, "X-Internal-Secret") && MessageDigest.isEqual(
                serviceInternalSecret.getBytes(StandardCharsets.UTF_8), internalSecret.getBytes(StandardCharsets.UTF_8))) {
            filterChain.doFilter(request, response);
            return;
        }

        // /internal/** is service identity only: a gateway-authenticated end user must never reach it.
        if (isInternalPath(request)) {
            writeUnauthorized(response);
            return;
        }

        String headerSecret = request.getHeader("X-Gateway-Secret");

        if (headerSecret == null || !MessageDigest.isEqual(
                headerSecret.getBytes(StandardCharsets.UTF_8), gatewaySecret.getBytes(StandardCharsets.UTF_8))) {
            writeForbidden(response);
            return;
        }

        // A repeated identity header is ambiguous: getHeader() sees the first value while @RequestHeader joins all of them.
        if (IDENTITY_HEADERS.stream().anyMatch(name -> hasMultipleValues(request, name))) {
            writeForbidden(response);
            return;
        }

        // Identity headers are trusted only here: the gateway strips client-sent X-User-* and re-injects them from the JWT.
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.isBlank()) {
            List<GrantedAuthority> authorities = toAuthorities(request.getHeader("X-User-Role"));
            // A token carrying both OPERATOR and ARBITRATOR could propose and approve the same action (governance §5.2).
            if (authorities.contains(OPERATOR) && authorities.contains(ARBITRATOR)) {
                writeForbidden(response);
                return;
            }
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, authorities));
            SecurityContextHolder.setContext(context);
        }

        filterChain.doFilter(request, response);
    }

    static void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write(FORBIDDEN_BODY);
    }

    private static void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(UNAUTHORIZED_BODY);
    }

    // The Spring Security firewall has already rejected encoded slashes and dot segments, so the raw URI is safe to match.
    private static boolean isInternalPath(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.equals("/internal") || path.startsWith("/internal/");
    }

    private static boolean hasMultipleValues(HttpServletRequest request, String name) {
        Enumeration<String> values = request.getHeaders(name);
        if (values == null || !values.hasMoreElements()) {
            return false;
        }
        values.nextElement();
        return values.hasMoreElements();
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
