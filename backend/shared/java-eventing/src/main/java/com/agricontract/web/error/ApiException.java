package com.agricontract.web.error;

import org.springframework.http.HttpStatus;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Base for exceptions that map to one HTTP status and one stable error code. A service exception extends this
 * instead of getting its own @ExceptionHandler, and {@link ErrorEnvelopeExceptionHandler} renders it.
 */
public class ApiException extends RuntimeException {

    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]*$");

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public ApiException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = Objects.requireNonNull(status, "status");
        if (code == null || !CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("Error code must be UPPER_SNAKE_CASE: " + code);
        }
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
