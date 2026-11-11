package com.agricontract.user.domain.exception;

import lombok.Getter;

// 400: wrong code. The failed attempt is already counted when this is thrown.
@Getter
public class InvalidVerificationCodeException extends RuntimeException {
    public static final String REASON = "VERIFICATION_CODE_INVALID";

    private final int attemptsLeft;

    public InvalidVerificationCodeException(int attemptsLeft) {
        super(REASON + ": wrong code, " + attemptsLeft + " attempt(s) left");
        this.attemptsLeft = attemptsLeft;
    }
}
