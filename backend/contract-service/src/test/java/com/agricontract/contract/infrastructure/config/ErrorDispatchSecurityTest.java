package com.agricontract.contract.infrastructure.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #429: the container re-dispatches sendError (missing header, malformed body, unknown path) to /error as an ERROR
 * dispatch that carries no authentication. Without a rule for it the client saw an empty 403 instead of the real status.
 * MockMvc never performs that re-dispatch itself, so the tests send the ERROR dispatch the container would send.
 */
@WebMvcTest(controllers = ErrorDispatchSecurityTest.Probe.class)
@Import({SecurityConfig.class, ErrorDispatchSecurityTest.Probe.class})
@TestPropertySource(properties = {"gateway.internal-secret=gateway-secret", "service.internal-secret=service-secret"})
class ErrorDispatchSecurityTest {
    private static final String PROBE = "/api/v1/probe";

    @Autowired
    private MockMvc mvc;

    @RestController
    static class Probe {
        @GetMapping(PROBE)
        String probe() {
            return "reached";
        }
    }

    @Test
    void errorDispatchRendersTheRealStatusInsteadOfAnEmpty403() throws Exception {
        mvc.perform(get("/error").with(dispatch(DispatcherType.ERROR))
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 400)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, PROBE))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/error").with(dispatch(DispatcherType.ERROR))
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/api/v1/nothing-here"))
                .andExpect(status().isNotFound());
    }

    @Test
    void theErrorDispatchRuleDoesNotOpenBusinessRoutes() throws Exception {
        mvc.perform(get(PROBE).with(dispatch(DispatcherType.ERROR))).andExpect(denied());
        mvc.perform(get(PROBE)).andExpect(denied());
    }

    @Test
    void anOrdinaryRequestToErrorIsStillNotOpen() throws Exception {
        mvc.perform(get("/error")).andExpect(denied());
    }

    private static ResultMatcher denied() {
        return result -> assertThat(result.getResponse().getStatus()).isIn(401, 403);
    }

    private static RequestPostProcessor dispatch(DispatcherType type) {
        return request -> {
            request.setDispatcherType(type);
            return request;
        };
    }
}
