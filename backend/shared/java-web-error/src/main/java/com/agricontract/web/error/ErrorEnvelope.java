package com.agricontract.web.error;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

/**
 * Error body frozen as {@code ErrorEnvelope} in contracts/openapi/golden-flow-api.yaml. Every field is required on the
 * wire; {@code code} is the machine-readable value callers branch on (UPPER_SNAKE_CASE), {@code message} is for humans.
 * {@code timestamp} is pinned to an ISO-8601 string so a service's ObjectMapper settings cannot turn it into a number.
 */
public record ErrorEnvelope(
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String traceId
) {
}
