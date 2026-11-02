package com.agricontract.product.common.exception;

// user-service unreachable or its answer unusable: the gate fails closed with 503 (matrix 21f).
public class EligibilityUnavailableException extends RuntimeException {
    public EligibilityUnavailableException() {
        super("Eligibility check is temporarily unavailable");
    }
}
