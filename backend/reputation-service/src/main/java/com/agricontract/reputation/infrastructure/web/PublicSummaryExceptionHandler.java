package com.agricontract.reputation.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Keeps unexpected summary failures inside MVC, before a secondary error dispatch. */
@RestControllerAdvice(assignableTypes = PublicSummaryController.class)
public class PublicSummaryExceptionHandler {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpectedFailure(HttpServletRequest request) {
        // Do not serialize an exception, its message, or its cause. The controller's
        // local validation handler still owns malformed UUID responses (400).
        return ResponseEntity.internalServerError()
                .body(ErrorResponse.of(request, 500, "Internal Server Error", "INTERNAL_SERVER_ERROR"));
    }
}
