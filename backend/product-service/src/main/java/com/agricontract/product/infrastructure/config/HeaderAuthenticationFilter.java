package com.agricontract.product.infrastructure.config;

import com.agricontract.web.error.ErrorEnvelope;
import com.agricontract.web.error.ErrorEnvelopeExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

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

        // /internal/** is service identity only: a gateway-authenticated end user must never reach it.
        if (isInternalPath(request)) {
            writeError(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Unauthorized");
            return;
        }

        String headerSecret = request.getHeader("X-Gateway-Secret");

        if (headerSecret == null || !MessageDigest.isEqual(
                headerSecret.getBytes(StandardCharsets.UTF_8), gatewaySecret.getBytes(StandardCharsets.UTF_8))) {
            writeError(request, response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Forbidden");
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

    // Rejected before any controller advice runs, so the shared ErrorEnvelope is written here (#424, D24).
    private static void writeError(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                                   String code, String message) throws IOException {
        String correlationId = request.getHeader(ErrorEnvelopeExceptionHandler.CORRELATION_HEADER);
        String traceId = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString() : correlationId;
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        JSON.writeValue(response.getWriter(), new ErrorEnvelope(Instant.now(), status.value(),
                status.getReasonPhrase(), code, message, request.getRequestURI(), traceId));
    }

    // The Spring Security firewall has already rejected encoded slashes and dot segments, so the raw URI is safe to match.
    private static boolean isInternalPath(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.equals("/internal") || path.startsWith("/internal/");
    }
}
