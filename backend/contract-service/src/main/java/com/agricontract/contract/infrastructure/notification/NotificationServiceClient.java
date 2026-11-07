package com.agricontract.contract.infrastructure.notification;

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
    record OtpEmailBody(String requestId, String recipientUserId, String recipientEmail, String contractId,
                        String otpCode, String signedContentHash, String expiresAt) {
        @Override
        public String toString() {
            return "OtpEmailBody[requestId=" + requestId + "]";
        }
    }
}
