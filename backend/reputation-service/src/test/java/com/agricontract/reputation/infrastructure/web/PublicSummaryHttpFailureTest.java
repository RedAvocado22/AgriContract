package com.agricontract.reputation.infrastructure.web;

import com.agricontract.reputation.application.usecase.GetPublicReputationSummary;
import com.agricontract.reputation.application.dto.PublicSummaryResult;
import com.agricontract.reputation.application.dto.PublicSummaryQuery;
import com.agricontract.reputation.infrastructure.config.SecurityConfiguration;
import com.agricontract.reputation.infrastructure.contract.FrozenSchemas;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {PublicSummaryController.class, ReputationErrorController.class},
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class,
        properties = "gateway.internal-secret=test-only-http-secret")
@Import({SecurityConfiguration.class, PublicSummaryExceptionHandler.class})
class PublicSummaryHttpFailureTest {
    static final UUID USER = UUID.fromString("e66a2861-599d-4000-96c0-783b5d128fb4");
    static final String PATH = "/api/v1/reputation/" + USER + "/public-summary";
    static final String PRIVATE_DETAILS = "SELECT * FROM completed_contract_fact; jdbc:mysql://private-db:3306/reputation_db password=private-value";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean GetPublicReputationSummary summary;

    @Test void databaseFailureIsAContractCompliant500WithoutPrivateDetails() throws Exception {
        when(summary.execute(new PublicSummaryQuery(USER))).thenThrow(new DataAccessResourceFailureException(PRIVATE_DETAILS));
        assertSafeServerError(auth(get(PATH)), PATH);
    }
    @Test void unexpectedApplicationFailureAlsoStaysA500() throws Exception {
        when(summary.execute(new PublicSummaryQuery(USER))).thenThrow(new IllegalStateException(PRIVATE_DETAILS));
        assertSafeServerError(auth(get(PATH)), PATH);
    }
    @Test void internalIllegalArgumentIsNotMisclassifiedAsAnInvalidPath() throws Exception {
        when(summary.execute(new PublicSummaryQuery(USER))).thenThrow(new IllegalArgumentException(PRIVATE_DETAILS));
        assertSafeServerError(auth(get(PATH)), PATH);
    }
    @Test void internalErrorDispatchKeeps500AfterAuthenticationContextWasCleared() throws Exception {
        assertSafeServerError(get("/error").servletPath("/error")
                .with(dispatch(DispatcherType.ERROR))
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, PATH)
                .requestAttr(RequestDispatcher.ERROR_MESSAGE, PRIVATE_DETAILS)
                .requestAttr(RequestDispatcher.ERROR_EXCEPTION, new RuntimeException(PRIVATE_DETAILS)), PATH);
        verifyNoInteractions(summary);
    }
    @Test void errorDispatchPermissionDoesNotExposeBusinessRoutes() throws Exception {
        mvc.perform(get(PATH).with(dispatch(DispatcherType.ERROR))).andExpect(status().isUnauthorized());
        verifyNoInteractions(summary);
    }
    @Test void errorRouteIsNotAccessibleAsAnOrdinaryRequestOrForward() throws Exception {
        mvc.perform(get("/error")).andExpect(status().isUnauthorized());
        mvc.perform(auth(get("/error"))).andExpect(status().isForbidden());
        mvc.perform(auth(get("/error").with(dispatch(DispatcherType.FORWARD)))).andExpect(status().isForbidden());
    }
    @Test void missingAndInvalidCredentialsStillFailBeforeCallingTheApplication() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).header("X-Gateway-Secret", "wrong").header("X-User-Id", USER)
                .header("X-User-Role", "BUYER")).andExpect(status().isUnauthorized());
        verifyNoInteractions(summary);
    }
    @ParameterizedTest @ValueSource(strings = {"invalid", "1-1-1-1-1"})
    void malformedUuidKeeps400(String id) throws Exception {
        var result = mvc.perform(auth(get("/api/v1/reputation/" + id + "/public-summary")))
                .andExpect(status().isBadRequest()).andReturn();
        FrozenSchemas.assertResponse("ErrorEnvelope", json.readTree(result.getResponse().getContentAsString()));
        verifyNoInteractions(summary);
    }
    @Test void successfulSummaryRemainsContractCompliant() throws Exception {
        when(summary.execute(new PublicSummaryQuery(USER))).thenReturn(new PublicSummaryResult(USER, null, false, List.of()));
        var result = mvc.perform(auth(get(PATH))).andExpect(status().isOk()).andReturn();
        FrozenSchemas.assertResponse("ReputationPublicSummary", json.readTree(result.getResponse().getContentAsString()));
    }
    void assertSafeServerError(MockHttpServletRequestBuilder request, String path) throws Exception {
        var result = mvc.perform(request).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500)).andExpect(jsonPath("$.path").value(path))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("Internal Server Error")).andReturn();
        String body = result.getResponse().getContentAsString();
        FrozenSchemas.assertResponse("ErrorEnvelope", json.readTree(body));
        assertThat(body).doesNotContain("SELECT", "jdbc:", "private-db", "password", "private-value", "Exception", "stackTrace");
    }
    static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request) {
        return request.header("X-Gateway-Secret", "test-only-http-secret")
                .header("X-User-Id", USER).header("X-User-Role", "BUYER");
    }
    static RequestPostProcessor dispatch(DispatcherType type) {
        return request -> { request.setDispatcherType(type); return request; };
    }
}
