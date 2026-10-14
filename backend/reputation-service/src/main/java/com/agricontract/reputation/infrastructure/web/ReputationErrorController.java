package com.agricontract.reputation.infrastructure.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Infrastructure fallback; security allows only an internal ERROR dispatch here. */
@RestController
public class ReputationErrorController implements ErrorController {
    @RequestMapping("/error")
    public ResponseEntity<ErrorResponse> error(HttpServletRequest request) {
        Object value = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus status = value instanceof Integer code ? HttpStatus.resolve(code) : null;
        if (status == null || !status.isError()) status = HttpStatus.INTERNAL_SERVER_ERROR;
        Object originalPath = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        String path = originalPath instanceof String uri ? uri : request.getRequestURI();
        // Exception/message attributes are deliberately ignored.
        return ResponseEntity.status(status).body(ErrorResponse.ofPath(path, status.value(),
                status.getReasonPhrase(), status.name()));
    }
}
