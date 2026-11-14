package com.agricontract.product.common.exception;

/**
 * A write was rejected by a database integrity rule, typically a concurrent insert of the same identity. Thrown by
 * repository implementations so callers never see persistence-framework exceptions.
 */
public class WriteConflictException extends RuntimeException {
    public WriteConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
