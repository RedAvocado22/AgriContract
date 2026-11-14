package com.agricontract.product.infrastructure.client;

import com.agricontract.product.application.dto.UserEligibility;
import com.agricontract.product.application.port.UserContactPort;
import com.agricontract.product.application.port.UserEligibilityPort;
import com.agricontract.product.common.exception.EligibilityUnavailableException;
import com.agricontract.product.common.exception.UserProfileNotFoundException;
import com.agricontract.product.common.exception.UserServiceUnavailableException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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
import java.util.Optional;

/**
 * The only reader of {@code GET /internal/v1/users/{userId}} in product-service, behind both user ports. The read is
 * fail-closed: only a well-formed 2xx body or a 404 (no profile) is an answer; timeouts, other statuses and malformed
 * bodies are failures, mapped to the exception each port declares.
 */
@Component
public class InternalUserClient implements UserEligibilityPort, UserContactPort {
    private static final Logger LOG = LoggerFactory.getLogger(InternalUserClient.class);
    private static final MediaType JSON_SUFFIX = new MediaType("application", "*+json");

    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient client;

    public InternalUserClient(RestClient.Builder builder,
                              @Value("${user-service.url}") String baseUrl,
                              @Value("${service.internal-secret}") String secret,
                              @Value("${user-service.connect-timeout-ms}") int connectTimeoutMs,
                              @Value("${user-service.read-timeout-ms}") int readTimeoutMs) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("SERVICE_INTERNAL_SECRET must be configured");
        }
        HttpClient transport = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(transport);
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        client = builder.requestFactory(factory).baseUrl(baseUrl)
                .defaultHeader("X-Internal-Secret", secret)
                .build();
    }

    @Override
    public Optional<UserEligibility> load(String userId) {
        try {
            return read(userId).map(body -> parseEligibility(userId, body));
        } catch (InvalidAnswer failure) {
            LOG.warn("eligibility_dependency_failure category={}", failure.category);
            throw new EligibilityUnavailableException();
        }
    }

    @Override
    public String getEmail(String userId) {
        JsonNode body;
        try {
            body = read(userId).orElseThrow(() -> new UserProfileNotFoundException(userId));
            requireIdentity(userId, body);
            return requiredText(body, "email");
        } catch (InvalidAnswer failure) {
            LOG.warn("contact_dependency_failure category={}", failure.category);
            throw new UserServiceUnavailableException("user-service contact lookup failed: " + failure.category, null);
        }
    }

    // Empty = 404 (no profile). Every other failure is an InvalidAnswer carrying a bounded category, so callers never
    // log the user id, the remote body or the raw exception.
    private Optional<JsonNode> read(String userId) {
        try {
            JsonNode body = client.get().uri("/internal/v1/users/{userId}", userId)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                            return null;
                        }
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new InvalidAnswer("dependency_http");
                        }
                        try {
                            MediaType contentType = response.getHeaders().getContentType();
                            if (contentType == null || !(MediaType.APPLICATION_JSON.isCompatibleWith(contentType)
                                    || JSON_SUFFIX.isCompatibleWith(contentType))) {
                                throw new InvalidAnswer("malformed_response");
                            }
                            return mapper.readTree(response.getBody());
                        } catch (JsonProcessingException | InvalidMediaTypeException malformed) {
                            throw new InvalidAnswer("malformed_response");
                        }
                    });
            if (body != null && !body.isObject()) {
                throw new InvalidAnswer("malformed_response");
            }
            return Optional.ofNullable(body);
        } catch (InvalidAnswer failure) {
            throw failure;
        } catch (ResourceAccessException failure) {
            throw new InvalidAnswer("transport");
        } catch (RuntimeException failure) {
            throw new InvalidAnswer("unexpected");
        }
    }

    private static UserEligibility parseEligibility(String requestedUserId, JsonNode body) {
        String userId = requireIdentity(requestedUserId, body);
        // Both keys are always written by user-service; a missing key must not read as "no expiry"/"not locked".
        return new UserEligibility(userId, requiredText(body, "verificationStatus"),
                nullableInstant(body, "authorizationExpiresAt"), nullableInstant(body, "lockedUntil"));
    }

    private static String requireIdentity(String requestedUserId, JsonNode body) {
        String userId = requiredText(body, "userId");
        if (!requestedUserId.equals(userId)) {
            throw new InvalidAnswer("identity_mismatch");
        }
        return userId;
    }

    private static Instant nullableInstant(JsonNode body, String field) {
        if (!body.has(field)) {
            throw new InvalidAnswer("malformed_response");
        }
        if (body.get(field).isNull()) {
            return null;
        }
        try {
            return Instant.parse(requiredText(body, field));
        } catch (DateTimeParseException malformed) {
            throw new InvalidAnswer("malformed_response");
        }
    }

    private static String requiredText(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new InvalidAnswer("malformed_response");
        }
        return value.textValue();
    }

    private static final class InvalidAnswer extends RuntimeException {
        private final String category;

        private InvalidAnswer(String category) {
            super(category, null, false, false);
            this.category = category;
        }
    }
}
