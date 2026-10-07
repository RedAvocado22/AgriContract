package com.agricontract.user.domain.exception;

public class SelfKycDecisionException extends RuntimeException {
    public SelfKycDecisionException(String userId) {
        super("Actor cannot decide KYC for their own profile: " + userId);
    }
}
