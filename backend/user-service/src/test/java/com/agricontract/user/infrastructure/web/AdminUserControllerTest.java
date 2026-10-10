package com.agricontract.user.infrastructure.web;

import com.agricontract.user.application.dto.AdminUserResponse;
import com.agricontract.user.application.dto.RejectUserCommand;
import com.agricontract.user.application.dto.VerifyUserCommand;
import com.agricontract.user.application.usecase.ListUsersByVerificationStatusUseCase;
import com.agricontract.user.application.usecase.RejectUserUseCase;
import com.agricontract.user.application.usecase.VerifyUserUseCase;
import com.agricontract.user.domain.exception.ConcurrentProfileUpdateException;
import com.agricontract.user.domain.exception.InvalidVerificationTransitionException;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    private static final String VERIFY_BODY = "{\"authorizationExpiresAt\":\"2027-06-30T16:59:59.999Z\"}";

    @Autowired private MockMvc mockMvc;

    @MockBean private ListUsersByVerificationStatusUseCase listUsersByVerificationStatusUseCase;
    @MockBean private VerifyUserUseCase verifyUserUseCase;
    @MockBean private RejectUserUseCase rejectUserUseCase;

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
                        .header("X-User-Role", "ADMIN")
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
    void verify_internalSecretWithForgedAdminRole_forbidden() throws Exception {
        mockMvc.perform(post(VERIFY_URL)
                        .header("X-Internal-Secret", INTERNAL_SECRET)
                        .header("X-User-Id", ACTOR_ID)
                        .header("X-User-Role", "ADMIN")
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
    void verify_asOperator_passesActorFromHeader() throws Exception {
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
    void verify_asAdmin_nullExpiryAllowed() throws Exception {
        when(verifyUserUseCase.execute(any())).thenReturn(response(VerificationStatus.VERIFIED));

        mockMvc.perform(viaGateway(post(VERIFY_URL), "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"authorizationExpiresAt\":null}"))
                .andExpect(status().isOk());

        ArgumentCaptor<VerifyUserCommand> captor = ArgumentCaptor.forClass(VerifyUserCommand.class);
        verify(verifyUserUseCase).execute(captor.capture());
        assertThat(captor.getValue().authorizationExpiresAt()).isNull();
    }

    @Test
    void reject_asAdmin_passesReasonAndActor() throws Exception {
        when(rejectUserUseCase.execute(any())).thenReturn(response(VerificationStatus.REJECTED));

        mockMvc.perform(viaGateway(post(REJECT_URL), "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Thieu giay phep\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("REJECTED"));

        verify(rejectUserUseCase).execute(new RejectUserCommand(TARGET_ID, ACTOR_ID, "Thieu giay phep"));
    }

    @Test
    void list_asOperator_returnsPage() throws Exception {
        when(listUsersByVerificationStatusUseCase.execute(eq(VerificationStatus.PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response(VerificationStatus.PENDING)), Pageable.ofSize(20), 1));

        mockMvc.perform(viaGateway(get("/api/v1/admin/users").param("verificationStatus", "PENDING"), "OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].userId").value(TARGET_ID))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    // --- error mapping ---

    @Test
    void verify_invalidTransition_conflict() throws Exception {
        when(verifyUserUseCase.execute(any()))
                .thenThrow(new InvalidVerificationTransitionException("verify", VerificationStatus.VERIFIED));

        mockMvc.perform(viaGateway(post(VERIFY_URL), "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content(VERIFY_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void reject_concurrentDecision_conflict() throws Exception {
        when(rejectUserUseCase.execute(any())).thenThrow(new ConcurrentProfileUpdateException(TARGET_ID));

        mockMvc.perform(viaGateway(post(REJECT_URL), "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Blurry ID scan\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void verify_selfDecision_forbidden() throws Exception {
        when(verifyUserUseCase.execute(any())).thenThrow(new SelfKycDecisionException(ACTOR_ID));

        mockMvc.perform(viaGateway(post(VERIFY_URL), "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content(VERIFY_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void verify_userNotFound_notFound() throws Exception {
        when(verifyUserUseCase.execute(any())).thenThrow(new NoSuchElementException("User not found: " + TARGET_ID));

        mockMvc.perform(viaGateway(post(VERIFY_URL), "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content(VERIFY_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void verify_expiryRejectedByDomain_badRequest() throws Exception {
        when(verifyUserUseCase.execute(any()))
                .thenThrow(new IllegalArgumentException("authorizationExpiresAt must be after the decision time"));

        mockMvc.perform(viaGateway(post(VERIFY_URL), "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content(VERIFY_BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verify_missingBody_badRequest() throws Exception {
        mockMvc.perform(viaGateway(post(VERIFY_URL), "ADMIN").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(verifyUserUseCase);
    }

    @Test
    void verify_malformedExpiry_badRequest() throws Exception {
        mockMvc.perform(viaGateway(post(VERIFY_URL), "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"authorizationExpiresAt\":\"30/06/2027\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(verifyUserUseCase);
    }

    @Test
    void reject_blankReason_badRequest() throws Exception {
        mockMvc.perform(viaGateway(post(REJECT_URL), "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(rejectUserUseCase);
    }

    @Test
    void list_invalidStatus_badRequest() throws Exception {
        mockMvc.perform(viaGateway(get("/api/v1/admin/users").param("verificationStatus", "APPROVED"), "ADMIN"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(listUsersByVerificationStatusUseCase);
    }
}
