package com.agricontract.contract.infrastructure.web;

import static com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.input;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agricontract.contract.application.dto.CoffeeContractView;
import com.agricontract.contract.application.usecase.*;
import com.agricontract.contract.domain.model.vo.ContractStatus;
import com.agricontract.contract.infrastructure.terms.JcsTermsCanonicalizer;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class UpdateContractTermsEndpointTest {
    private static final String TERMS_JSON = "{\"agreedPrice\":\"96000.00\",\"buyerDepositRate\":0.05}";

    private final JcsTermsCanonicalizer canonicalizer = new JcsTermsCanonicalizer();
    private final UpdateContractTermsUseCase useCase = mock(UpdateContractTermsUseCase.class);
    private MockMvc mvc;
    private String body;

    @BeforeEach
    void setUp() {
        ContractController controller = new ContractController(mock(CreateContractUseCase.class),
                mock(NegotiateContractUseCase.class), mock(SignContractUseCase.class),
                mock(GetContractUseCase.class), mock(ListContractsUseCase.class), useCase, canonicalizer);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        body = canonicalizer.canonicalInputJson(input());
        when(useCase.execute(any())).thenReturn(new CoffeeContractView(UUID.randomUUID().toString(), "listing-1",
                new BigDecimal("2500.5"), "buyer-1", "seller-1", ContractStatus.NEGOTIATING, TERMS_JSON,
                "c".repeat(64)));
    }

    @Test
    void returnsTheStoredTermsVerbatimWithoutTheApiResponseWrapper() throws Exception {
        mvc.perform(request(body, "SELLER").header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEGOTIATING"))
                .andExpect(jsonPath("$.terms.agreedPrice").value("96000.00"))
                .andExpect(jsonPath("$.signedContentHash").value("c".repeat(64)))
                .andExpect(jsonPath("$.milestones").isEmpty())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void serverDerivedFieldsInTheBodyAreRejected() throws Exception {
        String withGoods = body.replaceFirst("^\\{", "{\"goodsTerms\":{},");
        String withTotal = body.replaceFirst("^\\{", "{\"totalContractValue\":\"1.00\",");

        for (String bad : List.of(withGoods, withTotal)) {
            mvc.perform(request(bad, "BUYER").header("Idempotency-Key", UUID.randomUUID().toString()))
                    .andExpect(status().isBadRequest());
        }
        verify(useCase, never()).execute(any());
    }

    @Test
    void idempotencyKeyIsRequired() throws Exception {
        mvc.perform(request(body, "SELLER")).andExpect(status().isBadRequest());
        verify(useCase, never()).execute(any());
    }

    @Test
    void adminCannotReviseTermsForTheParties() throws Exception {
        mvc.perform(request(body, "ADMIN").header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
        verify(useCase, never()).execute(any());
    }

    private static MockHttpServletRequestBuilder request(
            String json, String role) {
        return patch("/api/v1/contracts/{id}/terms", UUID.randomUUID())
                .principal(new UsernamePasswordAuthenticationToken("seller-1", null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }
}
