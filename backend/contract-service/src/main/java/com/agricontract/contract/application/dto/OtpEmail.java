package com.agricontract.contract.application.dto;

import java.time.Instant;

/**
 * One OTP email to hand to notification-service (OtpEmailRequest). requestId is the challenge's otpId, so a
 * repeated call for the same challenge never sends a second email. The code is kept out of toString.
 */
public record OtpEmail(String requestId, String recipientUserId, String recipientEmail, String contractId,
                       String otpCode, String signedContentHash, Instant expiresAt, String purpose) {

    /** OtpEmailRequest.purpose (golden-flow-api, D42): absent means SIGN_CONTRACT, so signing sends none. */
    public static final String CANCEL_ACTIVATION = "CANCEL_ACTIVATION";

    /** A signing code: no purpose on the wire, exactly as before D42. */
    public OtpEmail(String requestId, String recipientUserId, String recipientEmail, String contractId,
                    String otpCode, String signedContentHash, Instant expiresAt) {
        this(requestId, recipientUserId, recipientEmail, contractId, otpCode, signedContentHash, expiresAt, null);
    }
    @Override
    public String toString() {
        return "OtpEmail[requestId=" + requestId + ", contractId=" + contractId + "]";
    }
}
