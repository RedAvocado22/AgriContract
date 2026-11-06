package com.agricontract.inspection.application.exception;

public class InvalidEventPayloadException extends RuntimeException {
    public InvalidEventPayloadException(String message) {
        super(message);
    }
}
