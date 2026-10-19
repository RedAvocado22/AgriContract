package com.agricontract.user.domain.exception;

// Restricted data is never returned without its read-audit entry (fail-closed, data-governance §8).
public class ReadAuditUnavailableException extends RuntimeException {
    public ReadAuditUnavailableException(String resourceId, Throwable cause) {
        super("Read audit unavailable for " + resourceId, cause);
    }
}
