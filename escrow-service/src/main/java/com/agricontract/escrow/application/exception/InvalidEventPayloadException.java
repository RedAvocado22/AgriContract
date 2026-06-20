package com.agricontract.escrow.application.exception;

public class InvalidEventPayloadException extends RuntimeException {
    public InvalidEventPayloadException(String message) {
        super(message);
    }
}
