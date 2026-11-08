package com.agricontract.contract.infrastructure.notification;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "notification-service", url = "${notification-service.url}",
        configuration = NotificationFeignConfig.class)
public interface NotificationServiceClient {

    /** sendOtpEmail: 200 with an empty body when the mail server accepted the email. */
    @PostMapping("/internal/v1/notifications/otp-email")
    void sendOtpEmail(@RequestBody OtpEmailBody body);

    /** OtpEmailRequest from golden-flow-api.yaml, field for field. */
    // purpose is left out of the JSON when null: notification-service refuses unknown fields, and a signing code
    // must keep the exact pre-D42 body.
    record OtpEmailBody(String requestId, String recipientUserId, String recipientEmail, String contractId,
                        String otpCode, String signedContentHash, String expiresAt,
                        @JsonInclude(JsonInclude.Include.NON_NULL) String purpose) {
        @Override
        public String toString() {
            return "OtpEmailBody[requestId=" + requestId + "]";
        }
    }
}
