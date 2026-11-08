package com.agricontract.contract.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agricontract.contract.application.dto.InitiateSignCommand;
import com.agricontract.contract.application.dto.SignChallengeView;
import com.agricontract.contract.application.dto.VerifySignCommand;
import com.agricontract.contract.application.exception.OtpEmailNotSentException;
import com.agricontract.contract.application.exception.SignerNotEligibleException;
import com.agricontract.contract.application.exception.UserServiceUnavailableException;
import com.agricontract.contract.application.usecase.InitiateSignUseCase;
import com.agricontract.contract.application.usecase.VerifyOtpAndSignUseCase;
import com.agricontract.contract.domain.exception.ContractConflictException;
import com.agricontract.contract.domain.model.consent.SignerRole;
import com.agricontract.contract.infrastructure.config.GatewayAuthDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ContractSigningEndpointTest {
    private static final String HASH = "c".repeat(64);
    private static final String BODY = "{\"signerRole\":\"SELLER\",\"consentStatementVersion\":\"1.0\","
            + "\"presentedContentHash\":\"" + HASH + "\"}";
    private static final Instant AUTH_TIME = Instant.parse("2026-11-08T09:59:00Z");
    private static final UUID OTP_ID = UUID.randomUUID();
    private static final String VERIFY_BODY = "{\"otpId\":\"" + OTP_ID + "\",\"otpCode\":\"123456\"}";

    private final InitiateSignUseCase useCase = mock(InitiateSignUseCase.class);
    private final VerifyOtpAndSignUseCase verifyUseCase = mock(VerifyOtpAndSignUseCase.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mvc = MockMvcBuilders.standaloneSetup(new ContractSigningController(useCase, verifyUseCase,
                        new ClientIpResolver(), mapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                // Same order as Spring's defaults: the raw String body is read before Jackson gets a chance.
                .setMessageConverters(new StringHttpMessageConverter(),
                        new MappingJackson2HttpMessageConverter(mapper))
                .build();
        when(useCase.execute(any())).thenReturn(new SignChallengeView(OTP_ID, Instant.parse("2026-11-08T10:05:00Z")));
    }

    @Test
    void returnsTheChallengeBare_andPassesTheGatewayAuthTime() throws Exception {
        UUID key = UUID.randomUUID();

        mvc.perform(request(BODY, "SELLER").header("Idempotency-Key", key.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.otpId").value(OTP_ID.toString()))
                .andExpect(jsonPath("$.expiresAt").value("2026-11-08T10:05:00Z"))
                .andExpect(jsonPath("$.data").doesNotExist());

        ArgumentCaptor<InitiateSignCommand> sent = ArgumentCaptor.forClass(InitiateSignCommand.class);
        verify(useCase).execute(sent.capture());
        assertThatCommand(sent.getValue(), key);
    }

    @Test
    void unknownFieldMissingFieldOrBadHash_isBadRequest() throws Exception {
        for (String bad : List.of(
                BODY.replace("}", ",\"otpCode\":\"123456\"}"),
                "{\"signerRole\":\"SELLER\",\"presentedContentHash\":\"" + HASH + "\"}",
                BODY.replace(HASH, HASH.toUpperCase()),
                BODY.replace("SELLER", "ADMIN"),
                "not json")) {
            mvc.perform(request(bad, "SELLER").header("Idempotency-Key", UUID.randomUUID().toString()))
                    .andExpect(status().isBadRequest());
        }
        verify(useCase, never()).execute(any());
    }

    @Test
    void idempotencyKeyIsRequired() throws Exception {
        mvc.perform(request(BODY, "SELLER")).andExpect(status().isBadRequest());
        verify(useCase, never()).execute(any());
    }

    @Test
    void onlyBuyerOrSellerMaySign() throws Exception {
        for (String role : List.of("ADMIN", "OPERATOR", "ARBITRATOR", "INSPECTOR")) {
            mvc.perform(request(BODY, role).header("Idempotency-Key", UUID.randomUUID().toString()))
                    .andExpect(status().isForbidden());
        }
        verify(useCase, never()).execute(any());
    }

    @Test
    void refusalsMapToTheirStatus() throws Exception {
        expect(new SignerNotEligibleException(SignerNotEligibleException.STEP_UP_REQUIRED, "again"), 403,
                "STEP_UP_REQUIRED");
        expect(new UserServiceUnavailableException("down", null), 503, "USER_SERVICE_UNAVAILABLE");
        expect(new OtpEmailNotSentException("OTP_EMAIL_PROVIDER_FAILED", "502"), 502, "OTP_EMAIL_NOT_SENT");
    }

    @Test
    void verify_returns204_andPassesCodeAuthTimeAndNoIp() throws Exception {
        UUID key = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();

        mvc.perform(verifyRequest(contractId, VERIFY_BODY, "SELLER").header("Idempotency-Key", key.toString()))
                .andExpect(status().isNoContent());

        ArgumentCaptor<VerifySignCommand> sent = ArgumentCaptor.forClass(VerifySignCommand.class);
        verify(verifyUseCase).execute(sent.capture());
        assertThat(sent.getValue()).isEqualTo(new VerifySignCommand(contractId.toString(), "seller-1", key, OTP_ID,
                "123456", AUTH_TIME, null));
    }

    @Test
    void verify_badBody_isBadRequest_withoutEchoingTheCode() throws Exception {
        for (String bad : List.of(
                VERIFY_BODY.replace("123456", "12345"),
                VERIFY_BODY.replace("123456", "12345a"),
                VERIFY_BODY.replace("}", ",\"signerRole\":\"SELLER\"}"),
                "{\"otpCode\":\"123456\"}",
                "not json")) {
            mvc.perform(verifyRequest(UUID.randomUUID(), bad, "SELLER")
                            .header("Idempotency-Key", UUID.randomUUID().toString()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value(not(containsString("12345"))));
        }
        mvc.perform(verifyRequest(UUID.randomUUID(), VERIFY_BODY, "SELLER")).andExpect(status().isBadRequest());
        verify(verifyUseCase, never()).execute(any());
    }

    @Test
    void verify_onlyBuyerOrSeller() throws Exception {
        mvc.perform(verifyRequest(UUID.randomUUID(), VERIFY_BODY, "ADMIN")
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
        verify(verifyUseCase, never()).execute(any());
    }

    @Test
    void verify_wrongCodeIs409_counterpartyIs409() throws Exception {
        for (String code : List.of(VerifyOtpAndSignUseCase.OTP_INVALID,
                ContractConflictException.COUNTERPARTY_NOT_ELIGIBLE)) {
            doThrow(new ContractConflictException(code, "no")).when(verifyUseCase).execute(any());
            mvc.perform(verifyRequest(UUID.randomUUID(), VERIFY_BODY, "SELLER")
                            .header("Idempotency-Key", UUID.randomUUID().toString()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value(startsWith(code)));
        }
    }

    private static MockHttpServletRequestBuilder verifyRequest(UUID contractId, String json, String role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("seller-1", null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        auth.setDetails(new GatewayAuthDetails(AUTH_TIME));
        return post("/api/v1/contracts/{id}/sign/verify", contractId)
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }

    private void expect(RuntimeException thrown, int status, String code) throws Exception {
        doThrow(thrown).when(useCase).execute(any());
        mvc.perform(request(BODY, "SELLER").header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.error").value(startsWith(code)));
    }

    private static void assertThatCommand(InitiateSignCommand command, UUID key) {
        assertThat(command).isEqualTo(new InitiateSignCommand(
                command.contractId(), "seller-1", key, SignerRole.SELLER, "1.0", HASH, AUTH_TIME));
    }

    private static MockHttpServletRequestBuilder request(String json, String role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("seller-1", null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        auth.setDetails(new GatewayAuthDetails(AUTH_TIME));
        return post("/api/v1/contracts/{id}/sign/initiate", UUID.randomUUID())
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }
}
