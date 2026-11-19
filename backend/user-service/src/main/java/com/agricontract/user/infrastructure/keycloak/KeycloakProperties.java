package com.agricontract.user.infrastructure.keycloak;

import org.springframework.boot.context.properties.ConfigurationProperties;

// The confidential client user-service signs in with (realm file, client "user-service", D59).
@ConfigurationProperties("keycloak")
public record KeycloakProperties(String url, String realm, String clientId, String clientSecret) {
}
