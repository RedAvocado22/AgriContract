package com.agricontract.product.common;

import java.time.Instant;

/**
 * Shared error envelope from the frozen contract (timestamp, status, error, code, message, path, traceId).
 * Used by the internal reservation endpoints, whose callers branch on {@code code}.
 */
public record ErrorEnvelope(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String traceId
) {
}
