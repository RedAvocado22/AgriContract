package com.agricontract.user.domain.exception;

import lombok.Getter;

// 409: the verification request cannot go ahead in the current state. reason is a stable machine-readable code.
@Getter
public class EmailVerificationConflictException extends RuntimeException {
    public static final String ALREADY_VERIFIED = "EMAIL_ALREADY_VERIFIED";
    public static final String RESEND_TOO_SOON = "VERIFICATION_RESEND_TOO_SOON";
    public static final String NOT_REQUESTED = "VERIFICATION_NOT_REQUESTED";
    public static final String EXPIRED = "VERIFICATION_EXPIRED";
    public static final String ATTEMPTS_EXHAUSTED = "VERIFICATION_ATTEMPTS_EXHAUSTED";

    private final String reason;

    public EmailVerificationConflictException(String reason, String message) {
        super(reason + ": " + message);
        this.reason = reason;
    }
}
