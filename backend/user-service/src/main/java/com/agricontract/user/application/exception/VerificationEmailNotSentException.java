package com.agricontract.user.application.exception;

// 503: notification-service did not confirm the email. The unsent code is expired, so the user can ask again at once.
public class VerificationEmailNotSentException extends RuntimeException {
    public VerificationEmailNotSentException(String message, Throwable cause) {
        super(message, cause);
    }
}
