package com.agricontract.user.infrastructure.keycloak;

import com.agricontract.user.application.exception.IdentityProviderUnavailableException;
import com.agricontract.user.application.port.IdentityAccounts;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keycloak Admin REST API as the service account of client "user-service", which holds only realm-management
 * view-users and manage-users (D59). Those roles cannot list a role's members, so candidates are the unverified users,
 * each then checked for a BUYER or SELLER realm role. Logs and exceptions never carry the token or the secret.
 */
@Component
@EnableConfigurationProperties(KeycloakProperties.class)
public class KeycloakIdentityAccounts implements IdentityAccounts {
    static final int CONNECT_TIMEOUT_MS = 1000;
    static final int READ_TIMEOUT_MS = 5000;
    static final int PAGE_SIZE = 100;
    private static final Set<String> SELF_REGISTERED_ROLES = Set.of("BUYER", "SELLER");
    // Renew a little before expiry so a token never runs out between the check and the call.
    private static final long TOKEN_MARGIN_SECONDS = 30;

    private final RestClient restClient;
    private final KeycloakProperties properties;
    private final Clock clock;
    private String token;
    private Instant tokenExpiresAt = Instant.EPOCH;

    public KeycloakIdentityAccounts(RestClient.Builder builder, KeycloakProperties properties, Clock clock) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MS);
        this.restClient = builder.clone().baseUrl(properties.url()).requestFactory(requestFactory).build();
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public List<String> unverifiedSelfRegisteredCreatedBefore(Instant cutoff) {
        List<String> due = new ArrayList<>();
        try {
            for (int first = 0; ; first += PAGE_SIZE) {
                KeycloakUser[] page = restClient.get()
                        .uri("/admin/realms/{realm}/users?emailVerified=false&briefRepresentation=false"
                                + "&first={first}&max={max}", properties.realm(), first, PAGE_SIZE)
                        .header("Authorization", bearer())
                        .retrieve()
                        .body(KeycloakUser[].class);
                if (page == null) {
                    break;
                }
                for (KeycloakUser user : page) {
                    if (user.isUnverifiedAndCreatedBefore(cutoff) && hasSelfRegisteredRole(user.id())) {
                        due.add(user.id());
                    }
                }
                if (page.length < PAGE_SIZE) {
                    break;
                }
            }
        } catch (RestClientException e) {
            throw unavailable("list unverified users", e);
        }
        return due;
    }

    @Override
    public void markEmailVerified(String userId) {
        try {
            restClient.put()
                    .uri("/admin/realms/{realm}/users/{id}", properties.realm(), userId)
                    .header("Authorization", bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("emailVerified", true))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw unavailable("mark email verified", e);
        }
    }

    @Override
    public void delete(String userId) {
        try {
            restClient.delete()
                    .uri("/admin/realms/{realm}/users/{id}", properties.realm(), userId)
                    .header("Authorization", bearer())
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound alreadyGone) {
            // Deleted by an earlier run whose profile delete did not commit, or by an admin.
        } catch (RestClientException e) {
            throw unavailable("delete user", e);
        }
    }

    // Direct realm roles: the registration provider (D47) maps BUYER or SELLER directly; staff never have them.
    private boolean hasSelfRegisteredRole(String userId) {
        KeycloakRole[] roles = restClient.get()
                .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", properties.realm(), userId)
                .header("Authorization", bearer())
                .retrieve()
                .body(KeycloakRole[].class);
        if (roles == null) {
            return false;
        }
        for (KeycloakRole role : roles) {
            if (SELF_REGISTERED_ROLES.contains(role.name())) {
                return true;
            }
        }
        return false;
    }

    private synchronized String bearer() {
        Instant now = Instant.now(clock);
        if (token == null || !now.isBefore(tokenExpiresAt)) {
            var form = new LinkedMultiValueMap<String, String>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", properties.clientId());
            form.add("client_secret", properties.clientSecret());
            TokenResponse response = restClient.post()
                    .uri("/realms/{realm}/protocol/openid-connect/token", properties.realm())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
            if (response == null || response.accessToken() == null) {
                throw new IdentityProviderUnavailableException("Keycloak returned no access token", null);
            }
            token = response.accessToken();
            tokenExpiresAt = now.plusSeconds(Math.max(0, response.expiresIn() - TOKEN_MARGIN_SECONDS));
        }
        return "Bearer " + token;
    }

    private static IdentityProviderUnavailableException unavailable(String action, RestClientException e) {
        String status = e instanceof RestClientResponseException http
                ? " (HTTP " + http.getStatusCode().value() + ")" : "";
        // Only the class and status: a Keycloak error body could echo request details.
        return new IdentityProviderUnavailableException("Keycloak could not " + action + status + ": "
                + e.getClass().getSimpleName(), null);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KeycloakUser(String id, Boolean emailVerified, Long createdTimestamp) {
        // An account without a creation time (imported seed) is never due.
        boolean isUnverifiedAndCreatedBefore(Instant cutoff) {
            return !Boolean.TRUE.equals(emailVerified) && createdTimestamp != null
                    && Instant.ofEpochMilli(createdTimestamp).isBefore(cutoff);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KeycloakRole(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(@JsonProperty("access_token") String accessToken,
                         @JsonProperty("expires_in") long expiresIn) {
    }
}
