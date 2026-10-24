package com.agricontract.user.infrastructure.web;

import com.agricontract.user.application.dto.PublicUserResponse;
import com.agricontract.user.application.dto.UserProfileResponse;
import com.agricontract.user.application.usecase.GetUserInfoUseCase;
import com.agricontract.user.application.usecase.GetUserProfileUseCase;
import com.agricontract.user.application.usecase.RegisterUserUseCase;
import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.VerificationStatus;
import com.agricontract.user.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.NoSuchElementException;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "gateway.internal-secret=test-gateway-secret",
        "service.internal-secret=test-internal-secret"
})
class UserControllerTest {

    private static final String GATEWAY_SECRET = "test-gateway-secret";
    private static final String INTERNAL_SECRET = "test-internal-secret";
    private static final String CALLER_ID = "buyer-1";
    private static final String TARGET_ID = "seller-1";
    private static final String PUBLIC_URL = "/api/v1/users/" + TARGET_ID;

    @Autowired private MockMvc mockMvc;

    @MockBean private GetUserProfileUseCase getUserProfileUseCase;
    @MockBean private RegisterUserUseCase registerUserUseCase;
    @MockBean private GetUserInfoUseCase getUserInfoUseCase;

    private MockHttpServletRequestBuilder viaGateway(MockHttpServletRequestBuilder request) {
        return request
                .header("X-Gateway-Secret", GATEWAY_SECRET)
                .header("X-User-Id", CALLER_ID)
                .header("X-User-Role", "default-roles-agricontract,BUYER");
    }

    // --- public user DTO ---

    @Test
    void getPublicUser_authenticated_returnsPublicFieldsOnly() throws Exception {
        when(getUserInfoUseCase.execute(TARGET_ID)).thenReturn(PublicUserResponse.builder()
                .userId(TARGET_ID).organizationName("HTX Ca Phe").role(Role.SELLER)
                .verificationStatus(VerificationStatus.VERIFIED).build());

        mockMvc.perform(viaGateway(get(PUBLIC_URL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(TARGET_ID))
                .andExpect(jsonPath("$.data.organizationName").value("HTX Ca Phe"))
                .andExpect(jsonPath("$.data.role").value("SELLER"))
                .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.phone").doesNotExist())
                .andExpect(jsonPath("$.data.address").doesNotExist());
    }

    @Test
    void getPublicUser_unknownUser_notFound() throws Exception {
        when(getUserInfoUseCase.execute(TARGET_ID)).thenThrow(new NoSuchElementException("User not found: " + TARGET_ID));

        mockMvc.perform(viaGateway(get(PUBLIC_URL)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPublicUser_withoutAnySecret_forbidden() throws Exception {
        mockMvc.perform(get(PUBLIC_URL).header("X-User-Id", CALLER_ID))
                .andExpect(status().isForbidden());
        verifyNoInteractions(getUserInfoUseCase);
    }

    @Test
    void getPublicUser_gatewaySecretWithoutIdentity_forbidden() throws Exception {
        mockMvc.perform(get(PUBLIC_URL).header("X-Gateway-Secret", GATEWAY_SECRET))
                .andExpect(status().isForbidden());
        verifyNoInteractions(getUserInfoUseCase);
    }

    @Test
    void getPublicUser_internalSecret_forbidden() throws Exception {
        // Service callers read GET /internal/v1/users/{userId}; the public path is for logged-in users only.
        mockMvc.perform(get(PUBLIC_URL).header("X-Internal-Secret", INTERNAL_SECRET))
                .andExpect(status().isForbidden());
        verifyNoInteractions(getUserInfoUseCase);
    }

    // --- own profile ---

    @Test
    void getMyProfile_readsOwnerFromPrincipal() throws Exception {
        when(getUserProfileUseCase.execute(CALLER_ID)).thenReturn(UserProfileResponse.builder()
                .userId(CALLER_ID).organizationName("Cong ty Xuat Khau").role(Role.BUYER)
                .email("buyer@agricontract.test").phone("0900000000").address("Ha Noi")
                .verificationStatus(VerificationStatus.PENDING).build());

        mockMvc.perform(viaGateway(get("/api/v1/users/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(CALLER_ID))
                .andExpect(jsonPath("$.data.email").value("buyer@agricontract.test"));

        verify(getUserProfileUseCase).execute(CALLER_ID);
    }

    @Test
    void getMyProfile_internalSecretWithForgedUserId_forbidden() throws Exception {
        // Before: the internal secret skipped identity, and /me trusted the raw X-User-Id header,
        // so any service could read anyone's email, phone and address.
        mockMvc.perform(get("/api/v1/users/me")
                        .header("X-Internal-Secret", INTERNAL_SECRET)
                        .header("X-User-Id", TARGET_ID))
                .andExpect(status().isForbidden());
        verifyNoInteractions(getUserProfileUseCase);
    }

    @Test
    void getMyProfile_gatewaySecretWithoutIdentity_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header("X-Gateway-Secret", GATEWAY_SECRET))
                .andExpect(status().isForbidden());
        verifyNoInteractions(getUserProfileUseCase);
    }
}
