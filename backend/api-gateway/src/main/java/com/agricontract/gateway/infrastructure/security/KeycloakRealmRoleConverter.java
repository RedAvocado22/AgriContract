package com.agricontract.gateway.infrastructure.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps Keycloak {@code realm_access.roles} to {@code ROLE_*} authorities, keeping only application roles.
 * Realm housekeeping roles (offline_access, uma_authorization, default-roles-*) never become authorities,
 * so neither the gateway role gate nor the X-User-Role header ever sees them.
 * Every canonical role is kept, including combinations: user-service rejects OPERATOR+ARBITRATOR itself,
 * so the gateway must forward the full set rather than hide one of them.
 */
public class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    public static final String ROLE_PREFIX = "ROLE_";

    static final Set<String> APPLICATION_ROLES = Set.of("BUYER", "SELLER", "ADMIN", "OPERATOR", "ARBITRATOR", "INSPECTOR");

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(APPLICATION_ROLES::contains)
                .distinct()
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role))
                .toList();
    }
}
