package com.agricontract.contract.infrastructure.web;

import com.agricontract.contract.application.exception.ContractNotFoundException;
import com.agricontract.contract.application.exception.InvalidEventPayloadException;
import com.agricontract.contract.application.exception.UnauthorizedContractActionException;
import com.agricontract.contract.common.ApiResponse;
import com.agricontract.contract.domain.exception.UnauthorizedContractAccessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
}
