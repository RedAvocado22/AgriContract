package com.agricontract.product.common.exception;

// Not VERIFIED, authorization expired, reputation-locked or no profile: 403 (user-service design §3).
public class ActorNotEligibleException extends RuntimeException {
    public ActorNotEligibleException() {
        super("Account is not eligible for this action");
    }
}
