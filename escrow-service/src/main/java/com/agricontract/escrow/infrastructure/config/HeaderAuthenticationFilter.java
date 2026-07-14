package com.agricontract.escrow.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    private final String gatewaySecret;

    public HeaderAuthenticationFilter(String gatewaySecret) {
        this.gatewaySecret = gatewaySecret;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String secretHeader = request.getHeader("X-Gateway-Secret");
        String userId = request.getHeader("X-User-Id");
        String roleHeader = request.getHeader("X-User-Role");

        if (secretHeader != null
                && MessageDigest.isEqual(gatewaySecret.getBytes(StandardCharsets.UTF_8), secretHeader.getBytes(StandardCharsets.UTF_8))
                && userId != null && !userId.isBlank() && roleHeader != null && !roleHeader.isBlank()) {
            List<GrantedAuthority> authorities = Arrays.stream(roleHeader.split(","))
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.trim().toUpperCase()))
                    .collect(Collectors.toList());

            Authentication auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
        } else if (secretHeader != null) {
            log.warn("Rejected request {} {}: invalid gateway secret or missing X-User-Id/X-User-Role header",
                    request.getMethod(), request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }
}
