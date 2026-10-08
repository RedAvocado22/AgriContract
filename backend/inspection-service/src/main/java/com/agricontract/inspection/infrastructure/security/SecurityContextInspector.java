package com.agricontract.inspection.infrastructure.security;

import com.agricontract.inspection.application.port.AuthenticatedInspectorPort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityContextInspector implements AuthenticatedInspectorPort {
    @Override
    public String requireInspectorId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new AuthenticationCredentialsNotFoundException("Authenticated identity is required");
        }
        if (authentication.getAuthorities().stream()
                .noneMatch(authority -> "ROLE_INSPECTOR".equals(authority.getAuthority()))) {
            throw new AccessDeniedException("INSPECTOR role is required");
        }
        return authentication.getName();
    }
}
