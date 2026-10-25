package com.agricontract.inspection.infrastructure.client;

import com.agricontract.inspection.application.exception.EligibilityUnavailableException;
import com.agricontract.inspection.application.port.InspectorEligibilityPort;
import com.agricontract.inspection.domain.model.InspectorEligibility;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;

@Component
public class InternalUserEligibilityClient implements InspectorEligibilityPort {
    private static final Logger LOG = LoggerFactory.getLogger(InternalUserEligibilityClient.class);
    private final ObjectMapper mapper = new ObjectMapper();
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
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new InvalidEligibility("dependency_http");
                        }
                        try {
                            var contentType = response.getHeaders().getContentType();
                            if (contentType == null || !(MediaType.APPLICATION_JSON.isCompatibleWith(contentType)
                                    || new MediaType("application", "*+json").isCompatibleWith(contentType))) {
                                throw new InvalidEligibility("malformed_response");
                            }
                            return mapper.readTree(response.getBody());
                        } catch (JsonProcessingException | InvalidMediaTypeException malformed) {
                            throw new InvalidEligibility("malformed_response");
                        }
                        // Other I/O failures propagate to RestClient's ResourceAccessException.
                    });
            if (body == null || !body.isObject() || !body.has("authorizationExpiresAt")) {
                throw new InvalidEligibility("malformed_response");
            }
            // Missing expiry must not be confused with an explicit unlimited license (JSON null).
            var expires = body.get("authorizationExpiresAt");
            Instant expiry = expires.isNull() ? null : Instant.parse(requiredText(body, "authorizationExpiresAt"));
            String userId = requiredText(body, "userId");
            if (!authenticatedUserId.equals(userId)) {
                throw new InvalidEligibility("identity_mismatch");
            }
            return new InspectorEligibility(userId, requiredText(body, "role"),
                    requiredText(body, "verificationStatus"), expiry);
        } catch (InvalidEligibility failure) {
            throw unavailable(failure.category);
        } catch (ResourceAccessException failure) {
            throw unavailable("transport");
        } catch (DateTimeParseException failure) {
            throw unavailable("malformed_response");
        } catch (RuntimeException failure) {
            // Unexpected client failures also fail closed, without mislabeling them as bad profiles.
            throw unavailable("unexpected");
        }
    }

    @Override
    public java.util.List<String> loadEligibleInspectors() {
        try {
            JsonNode body = client.get().uri("/internal/v1/users/inspectors/eligible")
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful())
                            throw new InvalidEligibility("dependency_http");
                        var type = response.getHeaders().getContentType();
                        if (type == null || !MediaType.APPLICATION_JSON.isCompatibleWith(type))
                            throw new InvalidEligibility("malformed_response");
                        return mapper.readTree(response.getBody());
                    });
            if (body == null || !body.isArray()) throw new InvalidEligibility("malformed_response");
            var ids = new java.util.LinkedHashSet<String>();
            for (JsonNode item : body) {
                if (!item.isObject() || item.size() != 1) throw new InvalidEligibility("malformed_response");
                String id = requiredText(item, "userId");
                String canonical = java.util.UUID.fromString(id).toString();
                if (!canonical.equalsIgnoreCase(id) || !ids.add(canonical))
                    throw new InvalidEligibility("malformed_response");
            }
            return java.util.List.copyOf(ids);
        } catch (InvalidEligibility failure) {
            throw unavailable(failure.category);
        } catch (ResourceAccessException failure) {
            throw unavailable("transport");
        } catch (RuntimeException failure) {
            throw unavailable("malformed_response");
        }
    }

    private static EligibilityUnavailableException unavailable(String category) {
        // Only bounded, locally defined categories: never pass the exception or remote data to the logger.
        LOG.warn("eligibility_dependency_failure category={}", category);
        return new EligibilityUnavailableException();
    }

    private static final class InvalidEligibility extends RuntimeException {
        private final String category;

        private InvalidEligibility(String category) {
            this.category = category;
        }
    }

    private static String requiredText(JsonNode body, String field) {
        var value = body.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new InvalidEligibility("malformed_response");
        }
        return value.textValue();
    }
}
