package com.agricontract.contract.infrastructure.web;

import com.agricontract.contract.application.exception.ContractNotFoundException;
import com.agricontract.contract.application.exception.IdempotencyKeyReusedException;
import com.agricontract.contract.application.exception.InvalidEventPayloadException;
import com.agricontract.contract.application.exception.ListingDataInvalidException;
import com.agricontract.contract.application.exception.ListingNotFoundException;
import com.agricontract.contract.application.exception.ListingServiceUnavailableException;
import com.agricontract.contract.application.exception.OtpEmailNotSentException;
import com.agricontract.contract.application.exception.SignerNotEligibleException;
import com.agricontract.contract.application.exception.UnauthorizedContractActionException;
import com.agricontract.contract.application.exception.UserServiceUnavailableException;
import com.agricontract.contract.domain.exception.ContractConflictException;
import com.agricontract.contract.domain.exception.UnauthorizedContractAccessException;
import com.agricontract.contract.domain.model.terms.InvalidContractTermsException;
import com.agricontract.web.error.ErrorEnvelope;
import com.agricontract.web.error.ErrorEnvelopeExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Every error is the shared ErrorEnvelope (D32): {@code code} is what the client branches on, {@code message} is for
 * humans, {@code traceId} ties the answer to the log. Validation, malformed requests and anything unexpected (500,
 * generic message) come from {@link ErrorEnvelopeExceptionHandler}.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ErrorEnvelopeExceptionHandler {

    @ExceptionHandler(ContractNotFoundException.class)
    public ResponseEntity<ErrorEnvelope> handleNotFound(ContractNotFoundException ex, HttpServletRequest request) {
        log.warn("404 CONTRACT_NOT_FOUND {}", ex.getMessage());
        return envelope(HttpStatus.NOT_FOUND, "CONTRACT_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler({UnauthorizedContractActionException.class, UnauthorizedContractAccessException.class})
    public ResponseEntity<ErrorEnvelope> handleUnauthorized(RuntimeException ex, HttpServletRequest request) {
        log.warn("403 FORBIDDEN {}", ex.getMessage());
        return envelope(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage(), request);
    }

    /** Caller may not start signing (step-up, profile, authorization, lock); the code tells the client what to do. */
    @ExceptionHandler(SignerNotEligibleException.class)
    public ResponseEntity<ErrorEnvelope> handleSignerNotEligible(SignerNotEligibleException ex,
                                                                 HttpServletRequest request) {
        log.warn("403 {}", ex.getCode());
        return envelope(HttpStatus.FORBIDDEN, ex.getCode(), ex.getMessage(), request);
    }

    /** Fail closed: without user-service the signer cannot be checked, so nothing is let through. */
    @ExceptionHandler(UserServiceUnavailableException.class)
    public ResponseEntity<ErrorEnvelope> handleUserServiceUnavailable(UserServiceUnavailableException ex,
                                                                      HttpServletRequest request) {
        log.warn("503 USER_SERVICE_UNAVAILABLE {}", ex.getMessage());
        return envelope(HttpStatus.SERVICE_UNAVAILABLE, "USER_SERVICE_UNAVAILABLE",
                "Your profile could not be checked right now; try again shortly.", request);
    }

    /** Fail closed (#232): without product-service no reservation can be taken, so no offer is stored. */
    @ExceptionHandler(ListingServiceUnavailableException.class)
    public ResponseEntity<ErrorEnvelope> handleListingServiceUnavailable(ListingServiceUnavailableException ex,
                                                                         HttpServletRequest request) {
        log.warn("503 PRODUCT_SERVICE_UNAVAILABLE {}", ex.getMessage());
        return envelope(HttpStatus.SERVICE_UNAVAILABLE, "PRODUCT_SERVICE_UNAVAILABLE",
                "The listing could not be reserved right now; try again shortly.", request);
    }

    /** #430: product-service answered with data that breaks the schema; retrying cannot help. Detail stays in the log. */
    @ExceptionHandler(ListingDataInvalidException.class)
    public ResponseEntity<ErrorEnvelope> handleListingDataInvalid(ListingDataInvalidException ex,
                                                                  HttpServletRequest request) {
        ResponseEntity<ErrorEnvelope> response = envelope(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Internal server error", request);
        log.error("500 LISTING_DATA_INVALID traceId={} {}", response.getBody().traceId(), ex.getMessage(), ex);
        return response;
    }

    @ExceptionHandler(ListingNotFoundException.class)
    public ResponseEntity<ErrorEnvelope> handleListingNotFound(ListingNotFoundException ex,
                                                               HttpServletRequest request) {
        log.warn("404 LISTING_NOT_FOUND {}", ex.getMessage());
        return envelope(HttpStatus.NOT_FOUND, "LISTING_NOT_FOUND", ex.getMessage(), request);
    }

    /** The code was not confirmed as sent and has already been made unusable; the user asks for a new one. */
    @ExceptionHandler(OtpEmailNotSentException.class)
    public ResponseEntity<ErrorEnvelope> handleOtpEmailNotSent(OtpEmailNotSentException ex,
                                                               HttpServletRequest request) {
        log.warn("502 OTP_EMAIL_NOT_SENT {}", ex.getCode());
        return envelope(HttpStatus.BAD_GATEWAY, "OTP_EMAIL_NOT_SENT",
                "The signing code could not be sent (" + ex.getCode() + "); request a new code.", request);
    }

    @ExceptionHandler(InvalidContractTermsException.class)
    public ResponseEntity<ErrorEnvelope> handleInvalidTerms(InvalidContractTermsException ex,
                                                            HttpServletRequest request) {
        log.warn("400 INVALID_CONTRACT_TERMS {}", ex.getMessage());
        return envelope(HttpStatus.BAD_REQUEST, "INVALID_CONTRACT_TERMS", ex.getMessage(), request);
    }

    @ExceptionHandler(ContractConflictException.class)
    public ResponseEntity<ErrorEnvelope> handleContractConflict(ContractConflictException ex,
                                                                HttpServletRequest request) {
        log.warn("409 {} {}", ex.getCode(), ex.getMessage());
        return envelope(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    public ResponseEntity<ErrorEnvelope> handleIdempotencyKeyReused(IdempotencyKeyReusedException ex,
                                                                    HttpServletRequest request) {
        log.warn("409 IDEMPOTENCY_KEY_REUSED");
        return envelope(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", ex.getMessage(), request);
    }

    /** Two writers raced on the same contract row; the later one must reload and retry. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorEnvelope> handleConcurrentUpdate(OptimisticLockingFailureException ex,
                                                                HttpServletRequest request) {
        log.warn("409 CONTRACT_CHANGED_CONCURRENTLY {}", ex.getMessage());
        return envelope(HttpStatus.CONFLICT, "CONTRACT_CHANGED_CONCURRENTLY",
                "The other party changed this contract at the same time; reload and retry.", request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorEnvelope> handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
        log.warn("409 CONTRACT_STATE_CONFLICT {}", ex.getMessage());
        return envelope(HttpStatus.CONFLICT, "CONTRACT_STATE_CONFLICT", ex.getMessage(), request);
    }

    @ExceptionHandler({IllegalArgumentException.class, InvalidEventPayloadException.class})
    public ResponseEntity<ErrorEnvelope> handleBadRequest(RuntimeException ex, HttpServletRequest request) {
        log.warn("400 VALIDATION_FAILED {}", ex.getMessage());
        return envelope(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", ex.getMessage(), request);
    }
}
