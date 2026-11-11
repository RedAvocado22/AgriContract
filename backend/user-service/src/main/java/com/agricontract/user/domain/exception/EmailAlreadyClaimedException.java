package com.agricontract.user.domain.exception;

// 409: another user has already proven this address. The message never names that user or repeats the address.
public class EmailAlreadyClaimedException extends RuntimeException {
    public static final String REASON = "EMAIL_ALREADY_CLAIMED";

    public EmailAlreadyClaimedException() {
        super(REASON + ": this email is already verified by another account");
    }
}
