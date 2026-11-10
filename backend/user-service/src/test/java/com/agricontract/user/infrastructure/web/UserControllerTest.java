package com.agricontract.user.infrastructure.web;

import com.agricontract.user.application.dto.PublicUserResponse;
import com.agricontract.user.application.dto.RegisterUserCommand;
import com.agricontract.user.application.dto.RegisterUserResult;
import com.agricontract.user.application.dto.UserProfileResponse;
import com.agricontract.user.application.usecase.GetUserInfoUseCase;
import com.agricontract.user.application.usecase.GetUserProfileUseCase;
import com.agricontract.user.application.usecase.RegisterUserUseCase;
import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.VerificationStatus;
import com.agricontract.user.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    void getPublicUser_gatewaySecretWithoutIdentity_unauthorized() throws Exception {
        mockMvc.perform(get(PUBLIC_URL).header("X-Gateway-Secret", GATEWAY_SECRET))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(getUserInfoUseCase);
    }

    @Test
    void getPublicUser_internalSecret_unauthorized() throws Exception {
        // Service callers read GET /internal/v1/users/{userId}; the public path is for logged-in users only.
        mockMvc.perform(get(PUBLIC_URL).header("X-Internal-Secret", INTERNAL_SECRET))
                .andExpect(status().isUnauthorized());
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
    void getMyProfile_internalSecretWithForgedUserId_unauthorized() throws Exception {
        // Before: the internal secret skipped identity, and /me trusted the raw X-User-Id header,
        // so any service could read anyone's email, phone and address.
        mockMvc.perform(get("/api/v1/users/me")
                        .header("X-Internal-Secret", INTERNAL_SECRET)
                        .header("X-User-Id", TARGET_ID))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(getUserProfileUseCase);
    }

    @Test
    void getMyProfile_gatewaySecretWithoutIdentity_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header("X-Gateway-Secret", GATEWAY_SECRET))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(getUserProfileUseCase);
    }

    // --- register (#153: email comes from the body, never from a header) ---

    private static final String REGISTER_URL = "/api/v1/users/register";

    private static UserProfileResponse profile(String email) {
        return UserProfileResponse.builder().userId(CALLER_ID).organizationName("Cong ty Mua").role(Role.BUYER)
                .email(email).verificationStatus(VerificationStatus.PENDING).build();
    }

    private static String registerBody(String emailJson) {
        return "{\"organizationName\":\"Cong ty Mua\"" + (emailJson == null ? "" : ",\"email\":" + emailJson) + "}";
    }

    @Test
    void register_new_takesEmailFromBody_is201() throws Exception {
        when(registerUserUseCase.execute(any())).thenReturn(new RegisterUserResult(profile("body@test.com"), true));

        mockMvc.perform(viaGateway(post(REGISTER_URL)).contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("\"body@test.com\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("body@test.com"));

        ArgumentCaptor<RegisterUserCommand> command = ArgumentCaptor.forClass(RegisterUserCommand.class);
        verify(registerUserUseCase).execute(command.capture());
        assertThat(command.getValue().userId()).isEqualTo(CALLER_ID);
        assertThat(command.getValue().email()).isEqualTo("body@test.com");
        assertThat(command.getValue().role()).isEqualTo(Role.BUYER);
    }

    @Test
    void register_forgedEmailHeaders_areIgnored() throws Exception {
        when(registerUserUseCase.execute(any())).thenReturn(new RegisterUserResult(profile("body@test.com"), true));

        // Two values used to be rejected (403) when X-User-Email was an identity header; it no longer is one.
        mockMvc.perform(viaGateway(post(REGISTER_URL)).contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Email", "forged-1@test.com").header("X-User-Email", "forged-2@test.com")
                        .content(registerBody("\"body@test.com\"")))
                .andExpect(status().isCreated());

        ArgumentCaptor<RegisterUserCommand> command = ArgumentCaptor.forClass(RegisterUserCommand.class);
        verify(registerUserUseCase).execute(command.capture());
        assertThat(command.getValue().email()).isEqualTo("body@test.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"<missing>", "\"\"", "\"not-an-email\""})
    void register_missingOrMalformedEmail_is400(String emailJson) throws Exception {
        mockMvc.perform(viaGateway(post(REGISTER_URL)).contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("<missing>".equals(emailJson) ? null : emailJson)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(registerUserUseCase);
    }

    @Test
    void register_existingUser_is200_withStoredEmail() throws Exception {
        when(registerUserUseCase.execute(any())).thenReturn(new RegisterUserResult(profile("stored@test.com"), false));

        mockMvc.perform(viaGateway(post(REGISTER_URL)).contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("\"body@test.com\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("stored@test.com"));
    }
}
