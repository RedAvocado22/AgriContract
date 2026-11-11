package com.agricontract.user.application.exception;

// 503: notification-service did not confirm the email. No code is kept, so the user simply asks again.
public class VerificationEmailNotSentException extends RuntimeException {
    public VerificationEmailNotSentException(String message, Throwable cause) {
        super(message, cause);
    }
}
