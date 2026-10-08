package com.agricontract.inspection.infrastructure.user;

import com.agricontract.inspection.application.port.InspectorEligibilityPort;
import com.agricontract.inspection.common.exception.EligibilityUnavailableException;
import com.agricontract.inspection.domain.model.InspectorEligibility;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;

@Component
public class InternalUserEligibilityClient implements InspectorEligibilityPort {
    private final RestClient client;

    public InternalUserEligibilityClient(RestClient.Builder builder,
            @Value("${inspection.user-service.base-url}") String baseUrl,
            @Value("${inspection.user-service.internal-secret}") String secret,
            @Value("${inspection.user-service.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${inspection.user-service.read-timeout-ms}") int readTimeoutMs) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("SERVICE_INTERNAL_SECRET must be configured");
        }
        var transport = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(transport);
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        client = builder.requestFactory(factory).baseUrl(baseUrl)
                .defaultHeader("X-Internal-Secret", secret).build();
    }

    @Override
    public InspectorEligibility load(String authenticatedUserId) {
        try {
            JsonNode body = client.get().uri("/internal/v1/users/{userId}", authenticatedUserId)
                    .retrieve().onStatus(status -> !status.is2xxSuccessful(), (request, response) -> {
                        throw new EligibilityUnavailableException();
                    }).body(JsonNode.class);
            if (body == null || !body.isObject() || !body.has("authorizationExpiresAt")) {
                throw new EligibilityUnavailableException();
            }
            // Missing expiry must not be confused with an explicit unlimited license (JSON null).
            var expires = body.get("authorizationExpiresAt");
            Instant expiry = expires.isNull() ? null : Instant.parse(requiredText(body, "authorizationExpiresAt"));
            String userId = requiredText(body, "userId");
            if (!authenticatedUserId.equals(userId)) {
                throw new EligibilityUnavailableException();
            }
            return new InspectorEligibility(userId, requiredText(body, "role"),
                    requiredText(body, "verificationStatus"), expiry);
        } catch (RuntimeException failure) {
            // Do not leak contact-bearing responses, credentials, or URLs through error messages.
            throw new EligibilityUnavailableException();
        }
    }

    private static String requiredText(JsonNode body, String field) {
        var value = body.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new EligibilityUnavailableException();
        }
        return value.textValue();
    }
}
