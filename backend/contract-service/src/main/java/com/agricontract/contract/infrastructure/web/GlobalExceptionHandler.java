package com.agricontract.contract.infrastructure.web;

import com.agricontract.contract.application.exception.OtpEmailNotSentException;
import com.agricontract.contract.application.exception.SignerNotEligibleException;
import com.agricontract.contract.application.exception.UserServiceUnavailableException;
import com.agricontract.contract.application.exception.ContractNotFoundException;
import com.agricontract.contract.application.exception.InvalidEventPayloadException;
import com.agricontract.contract.application.exception.UnauthorizedContractActionException;
import com.agricontract.contract.application.exception.IdempotencyKeyReusedException;
import com.agricontract.contract.common.ApiResponse;
import com.agricontract.contract.domain.exception.ContractConflictException;
import com.agricontract.contract.domain.model.terms.InvalidContractTermsException;
import com.agricontract.contract.domain.exception.UnauthorizedContractAccessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ContractNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ContractNotFoundException ex) {
        log.warn("404 {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedContractActionException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedContractActionException ex) {
        log.warn("403 {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedContractAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorizedAccess(UnauthorizedContractAccessException ex) {
        log.warn("403 {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(ex.getMessage()));
    }

    /** Caller may not start signing (step-up, profile, authorization, lock); the code tells the client what to do. */
    @ExceptionHandler(SignerNotEligibleException.class)
    public ResponseEntity<ApiResponse<Void>> handleSignerNotEligible(SignerNotEligibleException ex) {
        log.warn("403 {}", ex.getCode());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(ex.getCode() + ": " + ex.getMessage()));
    }

    /** Fail closed: without user-service the signer cannot be checked, so nothing is let through. */
    @ExceptionHandler(UserServiceUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUserServiceUnavailable(UserServiceUnavailableException ex) {
        log.warn("503 USER_SERVICE_UNAVAILABLE {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error(
                "USER_SERVICE_UNAVAILABLE: your profile could not be checked right now; try again shortly."));
    }

    /** The code was not confirmed as sent and has already been made unusable; the user asks for a new one. */
    @ExceptionHandler(OtpEmailNotSentException.class)
    public ResponseEntity<ApiResponse<Void>> handleOtpEmailNotSent(OtpEmailNotSentException ex) {
        log.warn("502 OTP_EMAIL_NOT_SENT {}", ex.getCode());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiResponse.error(
                "OTP_EMAIL_NOT_SENT: the signing code could not be sent (" + ex.getCode() + "); request a new code."));
    }

    @ExceptionHandler(InvalidContractTermsException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidTerms(InvalidContractTermsException ex) {
        log.warn("400 INVALID_CONTRACT_TERMS {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(ContractConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleContractConflict(ContractConflictException ex) {
        log.warn("409 {} {}", ex.getCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getCode() + ": " + ex.getMessage()));
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    public ResponseEntity<ApiResponse<Void>> handleIdempotencyKeyReused(IdempotencyKeyReusedException ex) {
        log.warn("409 IDEMPOTENCY_KEY_REUSED");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("IDEMPOTENCY_KEY_REUSED: " + ex.getMessage()));
    }

    /** Two writers raced on the same contract row; the later one must reload and retry. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleConcurrentUpdate(OptimisticLockingFailureException ex) {
        log.warn("409 CONTRACT_CHANGED_CONCURRENTLY {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(
                "CONTRACT_CHANGED_CONCURRENTLY: the other party changed this contract at the same time; reload and retry."));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
        log.warn("409 {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, InvalidEventPayloadException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(RuntimeException ex) {
        log.warn("400 {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("400 validation failed: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(message));
    }
}
