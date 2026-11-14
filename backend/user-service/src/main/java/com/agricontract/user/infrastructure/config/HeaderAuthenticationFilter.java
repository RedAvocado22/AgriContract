package com.agricontract.user.infrastructure.config;

import com.agricontract.web.error.ErrorEnvelope;
import com.agricontract.web.error.ErrorEnvelopeExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    private static final ObjectMapper JSON = JsonMapper.builder().findAndAddModules().build();

    private static final List<String> IDENTITY_HEADERS = List.of("X-User-Id", "X-User-Role");
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
            writeUnauthorized(request, response);
            return;
        }

        String headerSecret = request.getHeader("X-Gateway-Secret");

        if (headerSecret == null || !MessageDigest.isEqual(
                headerSecret.getBytes(StandardCharsets.UTF_8), gatewaySecret.getBytes(StandardCharsets.UTF_8))) {
            writeForbidden(request, response);
            return;
        }

        // A repeated identity header is ambiguous: getHeader() sees the first value while @RequestHeader joins all of them.
        if (IDENTITY_HEADERS.stream().anyMatch(name -> hasMultipleValues(request, name))) {
            writeForbidden(request, response);
            return;
        }

        // Identity headers are trusted only here: the gateway strips client-sent X-User-* and re-injects them from the JWT.
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.isBlank()) {
            List<GrantedAuthority> authorities = toAuthorities(request.getHeader("X-User-Role"));
            // A token carrying both OPERATOR and ARBITRATOR could propose and approve the same action (governance §5.2).
            if (authorities.contains(OPERATOR) && authorities.contains(ARBITRATOR)) {
                writeForbidden(request, response);
                return;
            }
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, authorities));
            SecurityContextHolder.setContext(context);
        }

        filterChain.doFilter(request, response);
    }

    static void writeForbidden(HttpServletRequest request, HttpServletResponse response) throws IOException {
        writeError(request, response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Forbidden");
    }

    static void writeUnauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        writeError(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Unauthorized");
    }

    // These rejections happen outside the DispatcherServlet, so the @RestControllerAdvice never sees them. The body is
    // still the shared ErrorEnvelope, so a caller parses every user-service error the same way.
    private static void writeError(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                                   String code, String message) throws IOException {
        String correlationId = request.getHeader(ErrorEnvelopeExceptionHandler.CORRELATION_HEADER);
        String traceId = correlationId != null && !correlationId.isBlank()
                ? correlationId : UUID.randomUUID().toString();
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
