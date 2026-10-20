package com.agricontract.user.infrastructure.web;

import com.agricontract.user.application.dto.InternalUserInfoResponse;
import com.agricontract.user.application.usecase.GetInternalUserInfoUseCase;
import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.VerificationStatus;
import com.agricontract.user.infrastructure.config.SecurityConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Boundary contract for GET /internal/v1/users/{userId} (golden-flow-api getInternalUserInfo, design §4.3). */
@WebMvcTest(InternalUserController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "gateway.internal-secret=test-gateway-secret",
        "service.internal-secret=test-internal-secret"
})
class InternalUserControllerTest {

    private static final String GATEWAY_SECRET = "test-gateway-secret";
    private static final String INTERNAL_SECRET = "test-internal-secret";
    private static final String URL = "/internal/v1/users/seller-1";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private GetInternalUserInfoUseCase getInternalUserInfoUseCase;

    @Test
    void validInternalSecret_returnsExactlyTheFrozenFieldsAtTopLevel() throws Exception {
        when(getInternalUserInfoUseCase.execute("seller-1")).thenReturn(new InternalUserInfoResponse(
                "seller-1", "HTX Ca Phe", "seller@agricontract.test", Role.SELLER, VerificationStatus.VERIFIED,
                Instant.parse("2027-06-30T16:59:59.999Z"), Instant.parse("2026-11-01T00:00:00Z")));

        String body = mockMvc.perform(get(URL).header("X-Internal-Secret", INTERNAL_SECRET))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userId").value("seller-1"))
                .andExpect(jsonPath("$.organizationName").value("HTX Ca Phe"))
                .andExpect(jsonPath("$.email").value("seller@agricontract.test"))
                .andExpect(jsonPath("$.role").value("SELLER"))
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.authorizationExpiresAt").value("2027-06-30T16:59:59.999Z"))
                .andExpect(jsonPath("$.lockedUntil").value("2026-11-01T00:00:00Z"))
                .andReturn().getResponse().getContentAsString();

        assertThat(fieldNames(body)).containsExactlyInAnyOrder("userId", "organizationName", "email", "role",
                "verificationStatus", "authorizationExpiresAt", "lockedUntil");
    }

    @Test
    void unlimitedAuthorizationAndNoLock_areWrittenAsExplicitNulls() throws Exception {
        when(getInternalUserInfoUseCase.execute("seller-1")).thenReturn(new InternalUserInfoResponse(
                "seller-1", "HTX Ca Phe", "seller@agricontract.test", Role.SELLER, VerificationStatus.PENDING,
                null, null));

        String body = mockMvc.perform(get(URL).header("X-Internal-Secret", INTERNAL_SECRET))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // A missing key means "malformed" to callers; only an explicit null means "no expiry" / "not locked".
        JsonNode json = objectMapper.readTree(body);
        assertThat(json.has("authorizationExpiresAt")).isTrue();
        assertThat(json.get("authorizationExpiresAt").isNull()).isTrue();
        assertThat(json.has("lockedUntil")).isTrue();
        assertThat(json.get("lockedUntil").isNull()).isTrue();
    }

    @Test
    void missingInternalSecret_returns401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());

        verifyNoInteractions(getInternalUserInfoUseCase);
    }

    @Test
    void wrongInternalSecret_returns401() throws Exception {
        mockMvc.perform(get(URL).header("X-Internal-Secret", "wrong-secret"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(getInternalUserInfoUseCase);
    }

    @Test
    void gatewayAuthenticatedEndUser_cannotReachInternalPath() throws Exception {
        mockMvc.perform(get(URL)
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", "admin-1")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(getInternalUserInfoUseCase);
    }

    @Test
    void unknownUser_returns404() throws Exception {
        when(getInternalUserInfoUseCase.execute(any())).thenThrow(new NoSuchElementException("User not found"));

        mockMvc.perform(get(URL).header("X-Internal-Secret", INTERNAL_SECRET))
                .andExpect(status().isNotFound());
    }

    @Test
    void databaseOutage_failsClosedWithoutUserData() throws Exception {
        when(getInternalUserInfoUseCase.execute(any())).thenThrow(new DataAccessResourceFailureException("db down"));

        mockMvc.perform(get(URL).header("X-Internal-Secret", INTERNAL_SECRET))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.verificationStatus").doesNotExist())
                .andExpect(jsonPath("$.userId").doesNotExist());
    }

    private List<String> fieldNames(String body) throws Exception {
        List<String> names = new ArrayList<>();
        objectMapper.readTree(body).fieldNames().forEachRemaining(names::add);
        return names;
    }
}
