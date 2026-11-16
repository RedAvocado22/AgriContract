package com.agricontract.user.infrastructure.web;

import lombok.Getter;

// 400 VERIFICATION_CODE_INVALID: thrown by the controller for a Rejected confirmation, after the counted attempt
// has committed.
@Getter
public class InvalidVerificationCodeException extends RuntimeException {
    public static final String REASON = "VERIFICATION_CODE_INVALID";

    private final int attemptsLeft;

    public InvalidVerificationCodeException(int attemptsLeft) {
        super(REASON + ": wrong code, " + attemptsLeft + " attempt(s) left");
        this.attemptsLeft = attemptsLeft;
    }
}
