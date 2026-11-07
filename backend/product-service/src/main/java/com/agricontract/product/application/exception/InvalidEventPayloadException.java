package com.agricontract.product.application.exception;

/**
 * An incoming event that does not match its frozen schema. Never retried: the listener sends it straight to the DLQ.
 * Messages name the field only, never its value, because listeners log the exception.
 */
public class InvalidEventPayloadException extends RuntimeException {

    public InvalidEventPayloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
