package com.agricontract.web.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Renders every error as {@link ErrorEnvelope}. Not registered on its own: a service opts in with
 * {@code @RestControllerAdvice class GlobalExceptionHandler extends ErrorEnvelopeExceptionHandler}, so a service that
 * still has its Phase 1 handler does not get two competing advices. Service-specific handlers go in the subclass and
 * build their body with {@link #envelope}.
 */
public abstract class ErrorEnvelopeExceptionHandler {

    /** Set by the gateway on every request (api-gateway design §2). */
    public static final String CORRELATION_HEADER = "X-Correlation-Id";

    private static final Logger log = LoggerFactory.getLogger(ErrorEnvelopeExceptionHandler.class);

    private final Clock clock;

    protected ErrorEnvelopeExceptionHandler() {
        this(Clock.systemUTC());
    }

    protected ErrorEnvelopeExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorEnvelope> handleApi(ApiException ex, HttpServletRequest request) {
        return envelope(ex.getStatus(), ex.getCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorEnvelope> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return envelope(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class, MissingRequestHeaderException.class})
    public ResponseEntity<ErrorEnvelope> handleMalformed(Exception ex, HttpServletRequest request) {
        return envelope(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Malformed request", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorEnvelope> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return envelope(HttpStatus.FORBIDDEN, "FORBIDDEN", "Forbidden", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorEnvelope> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
                                                                HttpServletRequest request) {
        return envelope(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", ex.getMessage(), request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorEnvelope> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex,
                                                                    HttpServletRequest request) {
        return envelope(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", ex.getMessage(), request);
    }

    // The message stays generic: exception text can carry SQL, PII or internals.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorEnvelope> handleUnexpected(Exception ex, HttpServletRequest request) {
        ResponseEntity<ErrorEnvelope> response =
                envelope(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal server error", request);
        log.error("Unhandled exception on {} traceId={}", request.getRequestURI(), response.getBody().traceId(), ex);
        return response;
    }

    protected ResponseEntity<ErrorEnvelope> envelope(HttpStatus status, String code, String message,
                                                     HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ErrorEnvelope(Instant.now(clock), status.value(),
                status.getReasonPhrase(), code, message, request.getRequestURI(), traceId(request)));
    }

    // traceId is required by the contract, so a request that bypassed the gateway (tests, internal calls) still
    // gets one.
    private static String traceId(HttpServletRequest request) {
        String correlationId = request.getHeader(CORRELATION_HEADER);
        return StringUtils.hasText(correlationId) ? correlationId : UUID.randomUUID().toString();
    }
}
