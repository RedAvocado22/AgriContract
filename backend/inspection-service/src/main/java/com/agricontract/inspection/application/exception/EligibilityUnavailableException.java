package com.agricontract.inspection.application.exception;

/** Translate to the frozen error catalog's 503 at a future operation boundary. */
public class EligibilityUnavailableException extends RuntimeException {
    public EligibilityUnavailableException() {
        super("Authoritative inspector eligibility could not be validated");
    }
}
