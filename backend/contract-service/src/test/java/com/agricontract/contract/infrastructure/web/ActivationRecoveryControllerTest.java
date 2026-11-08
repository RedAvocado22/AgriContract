package com.agricontract.contract.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agricontract.contract.application.dto.RetryDepositLockCommand;
import com.agricontract.contract.application.usecase.ActivationCancelUseCases;
import com.agricontract.contract.application.usecase.RetryDepositLockUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** D42: only OPERATOR retries a deposit lock; the parties stop the contract through their own cancel requests. */
class ActivationRecoveryControllerTest {
    private static final String OPERATOR = "9d8c7b6a-5f4e-4d3c-8b2a-1f0e9d8c7b6a";

    private final RetryDepositLockUseCase useCase = mock(RetryDepositLockUseCase.class);
    private final ActivationCancelUseCases cancel = mock(ActivationCancelUseCases.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ActivationRecoveryController(useCase, cancel, new ObjectMapper()))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void operatorRetry_is204_withTheOperatorAsActor() throws Exception {
        UUID contractId = UUID.randomUUID();
        UUID key = UUID.randomUUID();

        mvc.perform(post("/api/v1/contracts/{id}/activation/retry-deposit-lock", contractId)
                        .header("Idempotency-Key", key.toString()).principal(as("OPERATOR")))
                .andExpect(status().isNoContent());

        verify(useCase).execute(new RetryDepositLockCommand(contractId.toString(), OPERATOR, key));
    }

    @Test
    void partiesAndAdminCannotRetry_andTheKeyIsRequired() throws Exception {
        for (String role : List.of("BUYER", "SELLER", "ADMIN")) {
            mvc.perform(post("/api/v1/contracts/{id}/activation/retry-deposit-lock", UUID.randomUUID())
                            .header("Idempotency-Key", UUID.randomUUID().toString()).principal(as(role)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v1/contracts/{id}/activation/retry-deposit-lock", UUID.randomUUID())
                        .principal(as("OPERATOR")))
                .andExpect(status().isBadRequest());
        verify(useCase, never()).execute(any());
    }

    private static UsernamePasswordAuthenticationToken as(String role) {
        return new UsernamePasswordAuthenticationToken(OPERATOR, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
