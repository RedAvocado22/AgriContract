package com.agricontract.product.common.exception;

public class ReputationSignInRequiredException extends RuntimeException {
    public ReputationSignInRequiredException() {
        super("Sign in to filter listings by seller reputation");
    }
}
