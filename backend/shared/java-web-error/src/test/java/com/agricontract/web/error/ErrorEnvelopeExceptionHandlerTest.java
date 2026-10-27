package com.agricontract.web.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ErrorEnvelopeExceptionHandlerTest {

    private static final Instant NOW = Instant.parse("2026-10-27T03:00:00Z");

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new TestAdvice(Clock.fixed(NOW, ZoneOffset.UTC)))
                .build();
    }

    @Test
    void apiExceptionKeepsStatusAndCodeAndEchoesCorrelationId() throws Exception {
        mvc.perform(get("/conflict").header(ErrorEnvelopeExceptionHandler.CORRELATION_HEADER, "corr-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.timestamp").value("2026-10-27T03:00:00Z"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.code").value("TERMS_FROZEN"))
                .andExpect(jsonPath("$.message").value("Terms are frozen"))
                .andExpect(jsonPath("$.path").value("/conflict"))
                .andExpect(jsonPath("$.traceId").value("corr-1"));
    }

    @Test
    void missingCorrelationIdStillGetsTraceId() throws Exception {
        mvc.perform(get("/conflict"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void beanValidationIsBadRequest() throws Exception {
        mvc.perform(post("/validated").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void unreadableBodyIsBadRequest() throws Exception {
        mvc.perform(post("/validated").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void accessDeniedIsForbidden() throws Exception {
        mvc.perform(get("/denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void unexpectedExceptionHidesItsMessage() throws Exception {
        mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }

    @Test
    void subclassHandlerWinsOverBaseCatchAll() throws Exception {
        mvc.perform(get("/service-specific"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_FOUND"));
    }

    @Test
    void codeMustBeUpperSnakeCase() {
        assertThatThrownBy(() -> new ApiException(HttpStatus.CONFLICT, "termsFrozen", "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    record NamedRequest(@NotBlank String name) {
    }

    static class ListingNotFoundException extends RuntimeException {
    }

    @RestController
    static class TestController {

        @GetMapping("/conflict")
        void conflict() {
            throw new ApiException(HttpStatus.CONFLICT, "TERMS_FROZEN", "Terms are frozen");
        }

        @PostMapping("/validated")
        void validated(@Valid @RequestBody NamedRequest request) {
        }

        @GetMapping("/denied")
        void denied() {
            throw new AccessDeniedException("nope");
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("SELECT * FROM secret");
        }

        @GetMapping("/service-specific")
        void serviceSpecific() {
            throw new ListingNotFoundException();
        }
    }

    @RestControllerAdvice
    static class TestAdvice extends ErrorEnvelopeExceptionHandler {

        TestAdvice(Clock clock) {
            super(clock);
        }

        @ExceptionHandler(ListingNotFoundException.class)
        ResponseEntity<ErrorEnvelope> handleListingNotFound(HttpServletRequest request) {
            return envelope(HttpStatus.NOT_FOUND, "LISTING_NOT_FOUND", "Listing not found", request);
        }
    }
}
