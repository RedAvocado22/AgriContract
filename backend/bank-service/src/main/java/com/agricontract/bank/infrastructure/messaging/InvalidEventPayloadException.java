package com.agricontract.bank.infrastructure.messaging;

// The message cannot be read as a bank.lock_requested at all. Retrying cannot fix it, so it is registered as
// non-retryable and goes straight to the DLQ. The message never includes the payload.
public class InvalidEventPayloadException extends RuntimeException {

    public InvalidEventPayloadException(String message) {
        super(message);
    }
}
