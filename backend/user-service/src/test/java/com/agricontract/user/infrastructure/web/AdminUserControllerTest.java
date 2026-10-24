package com.agricontract.user.infrastructure.web;

import com.agricontract.user.application.dto.AdminUserResponse;
import com.agricontract.user.application.dto.AssignUserRoleCommand;
import com.agricontract.user.application.dto.ListUsersByVerificationStatusCommand;
import com.agricontract.user.application.dto.PageResult;
import com.agricontract.user.application.dto.RejectUserCommand;
import com.agricontract.user.application.dto.VerifyUserCommand;
import com.agricontract.user.application.usecase.AssignUserRoleUseCase;
import com.agricontract.user.application.usecase.ListUsersByVerificationStatusUseCase;
import com.agricontract.user.application.usecase.RejectUserUseCase;
import com.agricontract.user.application.usecase.VerifyUserUseCase;
import com.agricontract.user.domain.exception.ConcurrentProfileUpdateException;
import com.agricontract.user.domain.exception.InvalidVerificationTransitionException;
import com.agricontract.user.domain.exception.RoleSeparationViolationException;
import com.agricontract.user.domain.exception.SelfKycDecisionException;
import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.VerificationStatus;
import com.agricontract.user.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminUserController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "gateway.internal-secret=test-gateway-secret",
        "service.internal-secret=test-internal-secret"
})
class AdminUserControllerTest {

    private static final String GATEWAY_SECRET = "test-gateway-secret";
    private static final String INTERNAL_SECRET = "test-internal-secret";
    private static final String TARGET_ID = "seller-1";
    private static final String ACTOR_ID = "operator-1";
    private static final String VERIFY_URL = "/api/v1/admin/users/" + TARGET_ID + "/verify";
    private static final String REJECT_URL = "/api/v1/admin/users/" + TARGET_ID + "/reject";
    private static final String ROLE_URL = "/api/v1/admin/users/" + TARGET_ID + "/role";
    private static final String VERIFY_BODY = "{\"authorizationExpiresAt\":\"2027-06-30T16:59:59.999Z\"}";

    @Autowired private MockMvc mockMvc;

    @MockBean private ListUsersByVerificationStatusUseCase listUsersByVerificationStatusUseCase;
    @MockBean private VerifyUserUseCase verifyUserUseCase;
    @MockBean private RejectUserUseCase rejectUserUseCase;
    @MockBean private AssignUserRoleUseCase assignUserRoleUseCase;

    private MockHttpServletRequestBuilder viaGateway(MockHttpServletRequestBuilder request, String roles) {
        return request
                .header("X-Gateway-Secret", GATEWAY_SECRET)
                .header("X-User-Id", ACTOR_ID)
                .header("X-User-Role", roles);
    }

    private AdminUserResponse response(VerificationStatus status) {
        return AdminUserResponse.builder()
                .userId(TARGET_ID)
                .organizationName("HTX Ca Phe")
                .role(Role.SELLER)
                .email("seller@agricontract.test")
                .verificationStatus(status)
                .verifiedByActorId(ACTOR_ID)
                .verifiedAt(Instant.parse("2026-10-07T03:00:00Z"))
                .build();
    }

    // --- role boundary ---

    @Test
    void verify_withoutGatewaySecret_forbidden() throws Exception {
        mockMvc.perform(post(VERIFY_URL)
                        .header("X-User-Id", ACTOR_ID)
                        .header("X-User-Role", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON).content(VERIFY_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(verifyUserUseCase);
    }

    @Test
    void verify_asBuyer_forbidden() throws Exception {
        mockMvc.perform(viaGateway(post(VERIFY_URL), "BUYER,default-roles-agricontract")
                        .contentType(MediaType.APPLICATION_JSON).content(VERIFY_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(verifyUserUseCase);
    }

    @Test
    void verify_gatewaySecretWithoutIdentity_forbidden() throws Exception {
        mockMvc.perform(post(VERIFY_URL)
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .contentType(MediaType.APPLICATION_JSON).content(VERIFY_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(verifyUserUseCase);
    }

    @Test
    void verify_internalSecretWithForgedOperatorRole_forbidden() throws Exception {
        mockMvc.perform(post(VERIFY_URL)
                        .header("X-Internal-Secret", INTERNAL_SECRET)
                        .header("X-User-Id", ACTOR_ID)
                        .header("X-User-Role", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON).content(VERIFY_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(verifyUserUseCase);
    }

    @Test
    void list_asSeller_forbidden() throws Exception {
        mockMvc.perform(viaGateway(get("/api/v1/admin/users"), "SELLER"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(listUsersByVerificationStatusUseCase);
    }

    // --- happy paths ---

    @Test
    void verify_asOperator_passesActorFromPrincipal() throws Exception {
        when(verifyUserUseCase.execute(any())).thenReturn(response(VerificationStatus.VERIFIED));

        mockMvc.perform(viaGateway(post(VERIFY_URL), "default-roles-agricontract,OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON).content(VERIFY_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.data.verifiedByActorId").value(ACTOR_ID));

        ArgumentCaptor<VerifyUserCommand> captor = ArgumentCaptor.forClass(VerifyUserCommand.class);
        verify(verifyUserUseCase).execute(captor.capture());
        assertThat(captor.getValue().targetUserId()).isEqualTo(TARGET_ID);
        assertThat(captor.getValue().actorId()).isEqualTo(ACTOR_ID);
        assertThat(captor.getValue().authorizationExpiresAt()).isEqualTo(Instant.parse("2027-06-30T16:59:59.999Z"));
    }

    @Test
    void verify_asOperator_nullExpiryAllowed() throws Exception {
        when(verifyUserUseCase.execute(any())).thenReturn(response(VerificationStatus.VERIFIED));

        mockMvc.perform(viaGateway(post(VERIFY_URL), "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"authorizationExpiresAt\":null}"))
                .andExpect(status().isOk());

        ArgumentCaptor<VerifyUserCommand> captor = ArgumentCaptor.forClass(VerifyUserCommand.class);
        verify(verifyUserUseCase).execute(captor.capture());
        assertThat(captor.getValue().authorizationExpiresAt()).isNull();
    }

    @Test
    void reject_asOperator_passesReasonAndActor() throws Exception {
        when(rejectUserUseCase.execute(any())).thenReturn(response(VerificationStatus.REJECTED));

        mockMvc.perform(viaGateway(post(REJECT_URL), "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Thieu giay phep\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("REJECTED"));

        verify(rejectUserUseCase).execute(new RejectUserCommand(TARGET_ID, ACTOR_ID, "Thieu giay phep"));
    }

    @Test
    void list_asOperator_returnsPage() throws Exception {
        when(listUsersByVerificationStatusUseCase.execute(
                new ListUsersByVerificationStatusCommand(VerificationStatus.PENDING, 1, 2)))
                .thenReturn(new PageResult<>(List.of(response(VerificationStatus.PENDING)), 1, 2, 3));

        mockMvc.perform(viaGateway(get("/api/v1/admin/users")
                        .param("verificationStatus", "PENDING").param("page", "1").param("size", "2"), "OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].userId").value(TARGET_ID))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.first").value(false))
                .andExpect(jsonPath("$.data.last").value(true));
    }

    @Test
    void list_defaults_toFirstPageOfTwenty() throws Exception {
        when(listUsersByVerificationStatusUseCase.execute(any()))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0));

        mockMvc.perform(viaGateway(get("/api/v1/admin/users"), "OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.empty").value(true));

        verify(listUsersByVerificationStatusUseCase).execute(new ListUsersByVerificationStatusCommand(null, 0, 20));
    }

    @Test
    void list_sizeAboveLimit_badRequest() throws Exception {
        mockMvc.perform(viaGateway(get("/api/v1/admin/users").param("size", "101"), "OPERATOR"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(listUsersByVerificationStatusUseCase);
    }

    // --- actor identity ---

    @Test
    void verify_duplicateUserIdHeader_forbidden() throws Exception {
        mockMvc.perform(post(VERIFY_URL)
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", ACTOR_ID, "admin-2")
                        .header("X-User-Role", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON).content(VERIFY_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(verifyUserUseCase);
    }

    @Test
    void reject_duplicateRoleHeader_forbidden() throws Exception {
        mockMvc.perform(post(REJECT_URL)
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", ACTOR_ID)
                        .header("X-User-Role", "BUYER", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Thieu giay phep\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(rejectUserUseCase);
    }

    // --- error mapping ---

    @Test
    void verify_invalidTransition_conflict() throws Exception {
        when(verifyUserUseCase.execute(any()))
                .thenThrow(new InvalidVerificationTransitionException("verify", VerificationStatus.VERIFIED));

        mockMvc.perform(viaGateway(post(VERIFY_URL), "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON).content(VERIFY_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void reject_concurrentDecision_conflict() throws Exception {
        when(rejectUserUseCase.execute(any())).thenThrow(new ConcurrentProfileUpdateException(TARGET_ID));

        mockMvc.perform(viaGateway(post(REJECT_URL), "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Blurry ID scan\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void verify_selfDecision_forbidden() throws Exception {
        when(verifyUserUseCase.execute(any())).thenThrow(new SelfKycDecisionException(ACTOR_ID));

        mockMvc.perform(viaGateway(post(VERIFY_URL), "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON).content(VERIFY_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void verify_userNotFound_notFound() throws Exception {
        when(verifyUserUseCase.execute(any())).thenThrow(new NoSuchElementException("User not found: " + TARGET_ID));

        mockMvc.perform(viaGateway(post(VERIFY_URL), "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON).content(VERIFY_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void verify_expiryRejectedByDomain_badRequest() throws Exception {
        when(verifyUserUseCase.execute(any()))
                .thenThrow(new IllegalArgumentException("authorizationExpiresAt must be after the decision time"));

        mockMvc.perform(viaGateway(post(VERIFY_URL), "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON).content(VERIFY_BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verify_missingBody_badRequest() throws Exception {
        mockMvc.perform(viaGateway(post(VERIFY_URL), "OPERATOR").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(verifyUserUseCase);
    }

    // Contract: a missing key must never become an implicit unlimited grant (explicit null is covered above).
    @Test
    void verify_missingExpiryKey_badRequest() throws Exception {
        mockMvc.perform(viaGateway(post(VERIFY_URL), "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(verifyUserUseCase);
    }

    @Test
    void verify_malformedExpiry_badRequest() throws Exception {
        mockMvc.perform(viaGateway(post(VERIFY_URL), "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"authorizationExpiresAt\":\"30/06/2027\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(verifyUserUseCase);
    }

    @Test
    void verify_misspelledExpiryField_badRequest() throws Exception {
        mockMvc.perform(viaGateway(post(VERIFY_URL), "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authorizationExpiryAt\":\"2027-06-30T16:59:59.999Z\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(verifyUserUseCase);
    }

    @Test
    void verify_emptyStringExpiry_badRequest() throws Exception {
        mockMvc.perform(viaGateway(post(VERIFY_URL), "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"authorizationExpiresAt\":\"\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(verifyUserUseCase);
    }

    @Test
    void verify_epochNumberExpiry_badRequest() throws Exception {
        mockMvc.perform(viaGateway(post(VERIFY_URL), "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"authorizationExpiresAt\":1814371199}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(verifyUserUseCase);
    }

    @Test
    void verify_offsetExpiry_normalizedToUtc() throws Exception {
        when(verifyUserUseCase.execute(any())).thenReturn(response(VerificationStatus.VERIFIED));

        mockMvc.perform(viaGateway(post(VERIFY_URL), "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authorizationExpiresAt\":\"2027-06-30T23:59:59.999+07:00\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<VerifyUserCommand> captor = ArgumentCaptor.forClass(VerifyUserCommand.class);
        verify(verifyUserUseCase).execute(captor.capture());
        assertThat(captor.getValue().authorizationExpiresAt()).isEqualTo(Instant.parse("2027-06-30T16:59:59.999Z"));
    }

    @Test
    void reject_unknownField_badRequest() throws Exception {
        mockMvc.perform(viaGateway(post(REJECT_URL), "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Thieu giay phep\",\"actorId\":\"someone-else\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(rejectUserUseCase);
    }

    @Test
    void reject_blankReason_badRequest() throws Exception {
        mockMvc.perform(viaGateway(post(REJECT_URL), "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(rejectUserUseCase);
    }

    @Test
    void list_invalidStatus_badRequest() throws Exception {
        mockMvc.perform(viaGateway(get("/api/v1/admin/users").param("verificationStatus", "APPROVED"), "OPERATOR"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(listUsersByVerificationStatusUseCase);
    }

    // --- no ADMIN fallback on KYC ---

    @Test
    void verify_asAdmin_forbidden() throws Exception {
        mockMvc.perform(viaGateway(post(VERIFY_URL), "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content(VERIFY_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(verifyUserUseCase);
    }

    @Test
    void reject_asAdmin_forbidden() throws Exception {
        mockMvc.perform(viaGateway(post(REJECT_URL), "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Thieu giay phep\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(rejectUserUseCase);
    }

    @Test
    void list_asAdmin_forbidden() throws Exception {
        mockMvc.perform(viaGateway(get("/api/v1/admin/users"), "ADMIN"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(listUsersByVerificationStatusUseCase);
    }

    @Test
    void verify_asArbitrator_forbidden() throws Exception {
        mockMvc.perform(viaGateway(post(VERIFY_URL), "ARBITRATOR")
                        .contentType(MediaType.APPLICATION_JSON).content(VERIFY_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(verifyUserUseCase);
    }

    @Test
    void verify_tokenWithOperatorAndArbitrator_forbidden() throws Exception {
        mockMvc.perform(viaGateway(post(VERIFY_URL), "OPERATOR,ARBITRATOR")
                        .contentType(MediaType.APPLICATION_JSON).content(VERIFY_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(verifyUserUseCase);
    }

    @Test
    void unknownAdminPath_asAdmin_forbidden() throws Exception {
        mockMvc.perform(viaGateway(post("/api/v1/admin/users/" + TARGET_ID + "/lock"), "ADMIN"))
                .andExpect(status().isForbidden());
    }

    // --- role assignment ---

    @Test
    void assignRole_asAdmin_passesTargetAndRole() throws Exception {
        when(assignUserRoleUseCase.execute(any())).thenReturn(AdminUserResponse.builder()
                .userId(TARGET_ID).role(Role.ARBITRATOR).verificationStatus(VerificationStatus.PENDING).build());

        mockMvc.perform(viaGateway(put(ROLE_URL), "default-roles-agricontract,ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ARBITRATOR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ARBITRATOR"));

        verify(assignUserRoleUseCase).execute(new AssignUserRoleCommand(TARGET_ID, Role.ARBITRATOR));
    }

    @Test
    void assignRole_asOperator_forbidden() throws Exception {
        mockMvc.perform(viaGateway(put(ROLE_URL), "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ARBITRATOR\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(assignUserRoleUseCase);
    }

    @Test
    void assignRole_asArbitrator_forbidden() throws Exception {
        mockMvc.perform(viaGateway(put(ROLE_URL), "ARBITRATOR")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"OPERATOR\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(assignUserRoleUseCase);
    }

    @Test
    void assignRole_operatorArbitratorSwap_conflict() throws Exception {
        when(assignUserRoleUseCase.execute(any()))
                .thenThrow(new RoleSeparationViolationException(TARGET_ID, Role.OPERATOR, Role.ARBITRATOR));

        mockMvc.perform(viaGateway(put(ROLE_URL), "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ARBITRATOR\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.startsWith("ROLE_SEPARATION_VIOLATION")));
    }

    @Test
    void assignRole_userNotFound_notFound() throws Exception {
        when(assignUserRoleUseCase.execute(any())).thenThrow(new NoSuchElementException("User not found: " + TARGET_ID));

        mockMvc.perform(viaGateway(put(ROLE_URL), "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"OPERATOR\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void assignRole_unknownRole_badRequest() throws Exception {
        mockMvc.perform(viaGateway(put(ROLE_URL), "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"SUPERADMIN\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(assignUserRoleUseCase);
    }

    @Test
    void assignRole_integerRole_badRequest() throws Exception {
        // ordinal 2 is ADMIN; Jackson's default enum coercion would accept it
        mockMvc.perform(viaGateway(put(ROLE_URL), "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":2}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(assignUserRoleUseCase);
    }

    @Test
    void assignRole_numericStringRole_badRequest() throws Exception {
        mockMvc.perform(viaGateway(put(ROLE_URL), "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"2\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(assignUserRoleUseCase);
    }

    @Test
    void assignRole_nullRole_badRequest() throws Exception {
        mockMvc.perform(viaGateway(put(ROLE_URL), "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":null}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(assignUserRoleUseCase);
    }

    @Test
    void assignRole_lowercaseRole_badRequest() throws Exception {
        mockMvc.perform(viaGateway(put(ROLE_URL), "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"operator\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(assignUserRoleUseCase);
    }

    @Test
    void assignRole_missingRole_badRequest() throws Exception {
        mockMvc.perform(viaGateway(put(ROLE_URL), "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(assignUserRoleUseCase);
    }

    @Test
    void assignRole_extraRoleField_badRequest() throws Exception {
        mockMvc.perform(viaGateway(put(ROLE_URL), "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"OPERATOR\",\"roles\":[\"ARBITRATOR\"]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(assignUserRoleUseCase);
    }
}
