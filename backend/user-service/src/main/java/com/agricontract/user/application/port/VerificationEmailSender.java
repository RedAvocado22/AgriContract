package com.agricontract.user.application.port;

import java.time.Instant;

// Sends the email verification code. Throws VerificationEmailNotSentException unless the mail server accepted it.
public interface VerificationEmailSender {
    void send(String requestId, String userId, String recipientEmail, String code, Instant expiresAt);
}
