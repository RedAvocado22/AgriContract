package com.agricontract.inspection.infrastructure.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
 * #443: preserve the status when the container re-dispatches sendError to /error without authentication.
 * Adapted from Contract Service's #442 test: MockMvc does not perform container re-dispatches itself.
 */
@WebMvcTest(controllers = ErrorDispatchSecurityTest.Probe.class)
@Import({InspectionConfiguration.class, ErrorDispatchSecurityTest.Probe.class})
@TestPropertySource(properties = "gateway.internal-secret=gateway-secret")
class ErrorDispatchSecurityTest {
    private static final String PROBE = "/api/v1/inspections/probe";

    @Autowired
    private MockMvc mvc;

    @RestController
    static class Probe {
        @GetMapping(PROBE)
        String probe() {
            return "reached";
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 404})
    void errorDispatchRendersTheRealStatusInsteadOfAnEmpty403(int originalStatus) throws Exception {
        mvc.perform(get("/error").with(dispatch(DispatcherType.ERROR))
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, originalStatus)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, PROBE))
                .andExpect(status().is(originalStatus));
    }

    @Test
    void theErrorDispatchRuleDoesNotOpenBusinessRoutes() throws Exception {
        // Match a container error dispatch so gateway authentication does not mask authorization.
        mvc.perform(get(PROBE).with(dispatch(DispatcherType.ERROR))
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 400)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, PROBE))
                .andExpect(status().isForbidden());
        mvc.perform(get(PROBE)).andExpect(denied());
    }

    @Test
    void anOrdinaryRequestToErrorIsStillNotOpen() throws Exception {
        mvc.perform(get("/error")).andExpect(denied());
        mvc.perform(get("/error").with(gatewayIdentity())).andExpect(status().isForbidden());
    }

    @Test
    void aForwardToErrorIsStillDenied() throws Exception {
        mvc.perform(get("/error").with(dispatch(DispatcherType.FORWARD))
                        .with(gatewayIdentity())).andExpect(status().isForbidden());
    }

    @Test
    void authenticatedBusinessRequestsRetainDenyAll() throws Exception {
        mvc.perform(get(PROBE).with(gatewayIdentity())).andExpect(status().isForbidden());
    }

    private static RequestPostProcessor gatewayIdentity() {
        return request -> {
            request.addHeader("X-Gateway-Secret", "gateway-secret");
            request.addHeader("X-User-Id", "inspector-a");
            request.addHeader("X-User-Role", "INSPECTOR");
            return request;
        };
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
