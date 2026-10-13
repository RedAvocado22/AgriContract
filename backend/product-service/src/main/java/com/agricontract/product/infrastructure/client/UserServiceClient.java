package com.agricontract.product.infrastructure.client;

import com.agricontract.product.application.port.UserContactPort;
import com.agricontract.product.common.exception.UserProfileNotFoundException;
import com.agricontract.product.common.exception.UserServiceUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

@Component
public class UserServiceClient implements UserContactPort {

    private final RestClient restClient;

    @Autowired
    public UserServiceClient(RestClient.Builder builder,
                             @Value("${user-service.url}") String baseUrl,
                             @Value("${service.internal-secret}") String internalSecret) {
        this(builder
                .baseUrl(baseUrl)
                .requestFactory(timeoutRequestFactory())
                .defaultHeader("X-Internal-Secret", internalSecret)
                .build());
    }

    // Lets tests pass a RestClient bound to MockRestServiceServer.
    UserServiceClient(RestClient restClient) {
        this.restClient = restClient;
    }

    private static SimpleClientHttpRequestFactory timeoutRequestFactory() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        return requestFactory;
    }

    @Override
    public String getEmail(String userId) {
        InternalUserInfo body;
        try {
            body = restClient.get()
                    .uri("/internal/v1/users/{userId}", userId)
                    .retrieve()
                    .body(InternalUserInfo.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new UserProfileNotFoundException(userId);
            }
            throw new UserServiceUnavailableException("user-service rejected lookup for " + userId, e);
        } catch (RestClientException e) {
            throw new UserServiceUnavailableException("user-service lookup failed for " + userId, e);
        }

        if (body == null || body.email() == null || body.email().isBlank()) {
            throw new UserServiceUnavailableException("user-service returned no email for " + userId, null);
        }
        return body.email();
    }

    // GET /internal/v1/users/{userId} returns the bare InternalUserInfoResponse, not an ApiResponse envelope.
    record InternalUserInfo(String userId, String email) {
    }
}
