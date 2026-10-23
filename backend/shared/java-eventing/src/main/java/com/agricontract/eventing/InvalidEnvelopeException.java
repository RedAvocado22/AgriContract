package com.agricontract.eventing;

/**
 * Malformed or schema-violating envelope; consumers must treat it as non-retryable. The library cannot know each
 * service's retry policy, so a listener wraps it in its own InvalidEventPayloadException (already registered
 * non-retryable) rather than registering this type separately.
 */
public class InvalidEnvelopeException extends RuntimeException {

    public InvalidEnvelopeException(String message) {
        super(message);
    }
}
