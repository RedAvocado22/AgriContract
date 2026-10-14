package com.agricontract.reputation.infrastructure.web;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;
public record ErrorResponse(Instant timestamp, int status, String error, String code,
                            String message, String path, String traceId) {
    public static ErrorResponse of(HttpServletRequest request, int status, String error, String code) {
        return ofPath(request.getRequestURI(), status, error, code);
    }
    public static ErrorResponse ofPath(String path, int status, String error, String code) {
        return new ErrorResponse(Instant.now(), status, error, code, error,
                path, UUID.randomUUID().toString());
    }
}
