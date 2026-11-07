package com.agricontract.contract.application.exception;

public class IdempotencyKeyReusedException extends RuntimeException {
    public IdempotencyKeyReusedException() {
        super("This Idempotency-Key was already used for a different request; send a new key.");
    }
}
