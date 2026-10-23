package com.agricontract.eventing;

/** Malformed or schema-violating envelope; consumers must treat it as non-retryable. */
public class InvalidEnvelopeException extends RuntimeException {

    public InvalidEnvelopeException(String message) {
        super(message);
    }
}
