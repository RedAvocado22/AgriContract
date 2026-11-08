package com.agricontract.contract.infrastructure.notification;

import com.agricontract.contract.application.dto.OtpEmail;
import com.agricontract.contract.application.exception.OtpEmailNotSentException;
import com.agricontract.contract.application.port.OtpEmailPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.RetryableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.SocketTimeoutException;

/**
 * Any non-200 is "not sent". Logs carry the requestId and the error code only, never the code or the email.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OtpEmailPortAdapter implements OtpEmailPort {
    private final NotificationServiceClient client;
    private final ObjectMapper objectMapper;

    @Override
    public void send(OtpEmail email) {
        try {
            client.sendOtpEmail(new NotificationServiceClient.OtpEmailBody(email.requestId(), email.recipientUserId(),
                    email.recipientEmail(), email.contractId(), email.otpCode(), email.signedContentHash(),
                    email.expiresAt().toString(), email.purpose()));
        } catch (RetryableException noResponse) {
            String code = noResponse.getCause() instanceof SocketTimeoutException
                    ? OtpEmailNotSentException.TIMEOUT : OtpEmailNotSentException.UNREACHABLE;
            log.warn("OTP email {} not sent: {}", email.requestId(), code);
            throw new OtpEmailNotSentException(code, "notification-service gave no answer");
        } catch (FeignException failed) {
            String code = envelopeCode(failed);
            log.warn("OTP email {} not sent: HTTP {} {}", email.requestId(), failed.status(), code);
            throw new OtpEmailNotSentException(code, "notification-service answered " + failed.status());
        }
    }

    private String envelopeCode(FeignException failed) {
        try {
            JsonNode code = objectMapper.readTree(failed.contentUTF8()).get("code");
            return code != null && code.isTextual() ? code.asText() : OtpEmailNotSentException.UNKNOWN;
        } catch (Exception notJson) {
            return OtpEmailNotSentException.UNKNOWN;
        }
    }
}
