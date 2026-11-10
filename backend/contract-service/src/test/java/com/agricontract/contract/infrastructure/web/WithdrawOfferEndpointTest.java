package com.agricontract.contract.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agricontract.contract.application.dto.WithdrawOfferCommand;
import com.agricontract.contract.application.exception.ContractNotFoundException;
import com.agricontract.contract.application.usecase.*;
import com.agricontract.contract.domain.exception.ContractConflictException;
import com.agricontract.contract.domain.exception.UnauthorizedContractAccessException;
import com.agricontract.contract.infrastructure.terms.JcsTermsCanonicalizer;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** POST /api/v1/contracts/{id}/withdraw (#233, golden-flow-api withdrawOffer): empty body, 204. */
class WithdrawOfferEndpointTest {
    private static final UUID CONTRACT = UUID.randomUUID();

    private final WithdrawOfferUseCase useCase = mock(WithdrawOfferUseCase.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        ContractController controller = new ContractController(mock(CreateContractUseCase.class),
                mock(NegotiateContractUseCase.class), mock(GetContractUseCase.class),
                mock(ListContractsUseCase.class), mock(UpdateContractTermsUseCase.class), useCase,
                new JcsTermsCanonicalizer());
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void party_gets204_withNoBody_andTheCallerFromThePrincipal() throws Exception {
        UUID key = UUID.randomUUID();

        mvc.perform(request("BUYER").header("Idempotency-Key", key.toString()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        ArgumentCaptor<WithdrawOfferCommand> command = ArgumentCaptor.forClass(WithdrawOfferCommand.class);
        verify(useCase).execute(command.capture());
        assertThat(command.getValue())
                .isEqualTo(new WithdrawOfferCommand(CONTRACT.toString(), "user-1", key));
    }

    @Test
    void idempotencyKeyIsRequired() throws Exception {
        mvc.perform(request("SELLER")).andExpect(status().isBadRequest());
        verify(useCase, never()).execute(any());
    }

    @Test
    void staffCannotWithdrawForTheParties() throws Exception {
        for (String role : List.of("ADMIN", "OPERATOR", "ARBITRATOR")) {
            mvc.perform(request(role).header("Idempotency-Key", UUID.randomUUID().toString()))
                    .andExpect(status().isForbidden());
        }
        verify(useCase, never()).execute(any());
    }

    @Test
    void afterTheSecondSignature_is409() throws Exception {
        doThrow(new ContractConflictException(ContractConflictException.NOT_WITHDRAWABLE, "signed"))
                .when(useCase).execute(any());

        mvc.perform(request("SELLER").header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isConflict());
    }

    @Test
    void notAParty_is403_unknownContract_is404() throws Exception {
        doThrow(new UnauthorizedContractAccessException("not a party")).when(useCase).execute(any());
        mvc.perform(request("BUYER").header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());

        doThrow(new ContractNotFoundException(CONTRACT.toString())).when(useCase).execute(any());
        mvc.perform(request("BUYER").header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    private static MockHttpServletRequestBuilder request(String role) {
        return post("/api/v1/contracts/{id}/withdraw", CONTRACT)
                .principal(new UsernamePasswordAuthenticationToken("user-1", null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
