package com.agricontract.notification.application.exception;

public class InvalidEventPayloadException extends RuntimeException {
    public InvalidEventPayloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
