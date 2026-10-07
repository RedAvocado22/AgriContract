package com.agricontract.user.infrastructure.web;

import com.agricontract.user.common.ApiResponse;
import com.agricontract.user.common.exception.UserAlreadyExistsException;
import com.agricontract.user.domain.exception.InvalidVerificationTransitionException;
import com.agricontract.user.domain.exception.SelfKycDecisionException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(404).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        return ResponseEntity.status(409).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(InvalidVerificationTransitionException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidTransition(InvalidVerificationTransitionException ex) {
        return ResponseEntity.status(409).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(SelfKycDecisionException.class)
    public ResponseEntity<ApiResponse<Void>> handleSelfKycDecision(SelfKycDecisionException ex) {
        return ResponseEntity.status(403).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(400).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(400).body(ApiResponse.error("Malformed or missing request body"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(java.util.stream.Collectors.joining(", "));
        return ResponseEntity.status(400).body(ApiResponse.error(message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        return ResponseEntity.status(500).body(ApiResponse.error("Internal server error"));
    }

}
