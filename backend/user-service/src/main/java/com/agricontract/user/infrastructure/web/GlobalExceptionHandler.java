package com.agricontract.user.infrastructure.web;

import com.agricontract.user.common.exception.UserAlreadyExistsException;
import com.agricontract.user.application.exception.VerificationEmailNotSentException;
import com.agricontract.user.domain.exception.ConcurrentProfileUpdateException;
import com.agricontract.user.domain.exception.EmailAlreadyClaimedException;
import com.agricontract.user.domain.exception.EmailVerificationConflictException;
import com.agricontract.user.domain.exception.InvalidVerificationCodeException;
import com.agricontract.user.domain.exception.InvalidVerificationTransitionException;
import com.agricontract.user.domain.exception.ReadAuditUnavailableException;
import com.agricontract.user.domain.exception.RoleSeparationViolationException;
import com.agricontract.user.domain.exception.SelfKycDecisionException;
import com.agricontract.user.domain.exception.SelfRoleChangeException;
import com.agricontract.web.error.ErrorEnvelope;
import com.agricontract.web.error.ErrorEnvelopeExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

// Validation, malformed requests, access denied and the generic 500 come from the shared handler. Domain exceptions
// stay free of web types, so their status and code are mapped here. 409 codes follow the error-catalog families.
@RestControllerAdvice
public class GlobalExceptionHandler extends ErrorEnvelopeExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorEnvelope> handleNotFound(NoSuchElementException ex, HttpServletRequest request) {
        return envelope(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(EmailAlreadyClaimedException.class)
    public ResponseEntity<ApiResponse<Void>> handleEmailAlreadyClaimed(EmailAlreadyClaimedException ex) {
        return ResponseEntity.status(409).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(EmailVerificationConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleEmailVerificationConflict(EmailVerificationConflictException ex) {
        return ResponseEntity.status(409).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(InvalidVerificationCodeException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidVerificationCode(InvalidVerificationCodeException ex) {
        return ResponseEntity.status(400).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(VerificationEmailNotSentException.class)
    public ResponseEntity<ApiResponse<Void>> handleVerificationEmailNotSent(VerificationEmailNotSentException ex) {
        return ResponseEntity.status(503).body(ApiResponse.error("Verification email could not be sent; try again"));
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorEnvelope> handleUserAlreadyExists(UserAlreadyExistsException ex,
                                                                 HttpServletRequest request) {
        return envelope(HttpStatus.CONFLICT, "USER_ALREADY_EXISTS", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidVerificationTransitionException.class)
    public ResponseEntity<ErrorEnvelope> handleInvalidTransition(InvalidVerificationTransitionException ex,
                                                                 HttpServletRequest request) {
        return envelope(HttpStatus.CONFLICT, "VERIFICATION_STATE_CONFLICT", ex.getMessage(), request);
    }

    // Optimistic lock lost: the caller reloads the profile and retries.
    @ExceptionHandler(ConcurrentProfileUpdateException.class)
    public ResponseEntity<ErrorEnvelope> handleConcurrentUpdate(ConcurrentProfileUpdateException ex,
                                                                HttpServletRequest request) {
        return envelope(HttpStatus.CONFLICT, "USER_PROFILE_VERSION_MISMATCH", ex.getMessage(), request);
    }

    @ExceptionHandler(RoleSeparationViolationException.class)
    public ResponseEntity<ErrorEnvelope> handleRoleSeparation(RoleSeparationViolationException ex,
                                                              HttpServletRequest request) {
        return envelope(HttpStatus.CONFLICT, RoleSeparationViolationException.CODE, ex.getMessage(), request);
    }

    @ExceptionHandler(SelfKycDecisionException.class)
    public ResponseEntity<ErrorEnvelope> handleSelfKycDecision(SelfKycDecisionException ex,
                                                               HttpServletRequest request) {
        return envelope(HttpStatus.FORBIDDEN, "SELF_KYC_DECISION", ex.getMessage(), request);
    }

    @ExceptionHandler(SelfRoleChangeException.class)
    public ResponseEntity<ErrorEnvelope> handleSelfRoleChange(SelfRoleChangeException ex,
                                                              HttpServletRequest request) {
        return envelope(HttpStatus.FORBIDDEN, "SELF_ROLE_CHANGE", ex.getMessage(), request);
    }

    // Fail-closed: the audit store is a dependency of the read, so its failure is unavailability, not a client error.
    @ExceptionHandler(ReadAuditUnavailableException.class)
    public ResponseEntity<ErrorEnvelope> handleReadAuditUnavailable(ReadAuditUnavailableException ex,
                                                                    HttpServletRequest request) {
        return envelope(HttpStatus.SERVICE_UNAVAILABLE, "READ_AUDIT_UNAVAILABLE",
                "Service temporarily unavailable", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorEnvelope> handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        return envelope(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", ex.getMessage(), request);
    }
}
