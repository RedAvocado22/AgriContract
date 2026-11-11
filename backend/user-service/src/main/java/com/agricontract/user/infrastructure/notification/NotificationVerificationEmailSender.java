package com.agricontract.user.infrastructure.notification;

import com.agricontract.user.application.exception.VerificationEmailNotSentException;
import com.agricontract.user.application.port.VerificationEmailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;

/**
 * POST /internal/v1/notifications/otp-email with purpose VERIFY_EMAIL (golden-flow-api.yaml OtpEmailRequest).
 * Same timeouts as contract-service: notification-service bounds the call to 4500 ms, so a 5000 ms read timeout never
 * gives up while it is still sending. No retry: a second attempt is a new request with a new code.
 * Logs carry the requestId only, never the code or the address.
 */
@Component
@Slf4j
public class NotificationVerificationEmailSender implements VerificationEmailSender {
    static final String PATH = "/internal/v1/notifications/otp-email";
    static final String PURPOSE = "VERIFY_EMAIL";
    static final int CONNECT_TIMEOUT_MS = 1000;
    static final int READ_TIMEOUT_MS = 5000;

    private final RestClient restClient;

    public NotificationVerificationEmailSender(RestClient.Builder builder,
                                               @Value("${notification-service.url}") String baseUrl,
                                               @Value("${service.internal-secret}") String internalSecret) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MS);
        this.restClient = builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("X-Internal-Secret", internalSecret)
                .build();
    }

    @Override
    public void send(String requestId, String userId, String recipientEmail, String code, Instant expiresAt) {
        try {
            restClient.post()
                    .uri(PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new OtpEmailBody(requestId, userId, recipientEmail, code, expiresAt.toString(), PURPOSE))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Verification email {} not sent: {}", requestId, e.getClass().getSimpleName());
            throw new VerificationEmailNotSentException("notification-service did not accept the email", e);
        }
    }

    // OtpEmailRequest for purpose VERIFY_EMAIL: no contractId and no signedContentHash.
    record OtpEmailBody(String requestId, String recipientUserId, String recipientEmail, String otpCode,
                        String expiresAt, String purpose) {
        @Override
        public String toString() {
            return "OtpEmailBody[requestId=" + requestId + "]";
        }
    }
}
