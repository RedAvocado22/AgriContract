package com.agricontract.contract.application.dto;

import java.time.Instant;

/**
 * One OTP email to hand to notification-service (OtpEmailRequest). requestId is the challenge's otpId, so a
 * repeated call for the same challenge never sends a second email. The code is kept out of toString.
 */
public record OtpEmail(String requestId, String recipientUserId, String recipientEmail, String contractId,
                       String otpCode, String signedContentHash, Instant expiresAt) {
    @Override
    public String toString() {
        return "OtpEmail[requestId=" + requestId + ", contractId=" + contractId + "]";
    }
}
