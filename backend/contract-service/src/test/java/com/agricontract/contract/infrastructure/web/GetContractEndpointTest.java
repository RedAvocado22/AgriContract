package com.agricontract.contract.infrastructure.web;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agricontract.contract.application.dto.CoffeeContractView;
import com.agricontract.contract.application.dto.ContractDetail;
import com.agricontract.contract.application.dto.ContractResponse;
import com.agricontract.contract.application.usecase.*;
import com.agricontract.contract.domain.model.vo.ContractStatus;
import com.agricontract.contract.infrastructure.terms.JcsTermsCanonicalizer;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** The client reads the signedContentHash it must present to /sign/initiate from this endpoint. */
class GetContractEndpointTest {
    private static final String TERMS_JSON = "{\"agreedPrice\":\"95000.00\",\"buyerDepositRate\":0.05}";

    private final GetContractUseCase useCase = mock(GetContractUseCase.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        ContractController controller = new ContractController(mock(CreateContractUseCase.class),
                mock(NegotiateContractUseCase.class), useCase, mock(ListContractsUseCase.class),
                mock(UpdateContractTermsUseCase.class), new JcsTermsCanonicalizer());
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void coffeeContractIsTheFrozenContractResponseWithItsHashAndNoWrapper() throws Exception {
        String id = UUID.randomUUID().toString();
        when(useCase.execute(anyString(), anyString(), anyBoolean())).thenReturn(new ContractDetail(
                new CoffeeContractView(id, "listing-1", new BigDecimal("2500.5"), "buyer-1", "seller-1",
                        ContractStatus.OFFERED, TERMS_JSON, "d".repeat(64)), null));

        mvc.perform(get("/api/v1/contracts/{id}", id).principal(buyer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractId").value(id))
                .andExpect(jsonPath("$.status").value("OFFERED"))
                .andExpect(jsonPath("$.reservedQuantity").value(2500.5))
                .andExpect(jsonPath("$.terms.agreedPrice").value("95000.00"))
                .andExpect(jsonPath("$.signedContentHash").value("d".repeat(64)))
                .andExpect(jsonPath("$.milestones").isEmpty())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void phaseOneRowWithoutCoffeeTermsKeepsItsWrappedBody() throws Exception {
        when(useCase.execute(anyString(), anyString(), anyBoolean()))
                .thenReturn(new ContractDetail(null, legacyResponse()));

        mvc.perform(get("/api/v1/contracts/{id}", "seed-ctr-offered").principal(buyer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.contractId").value("seed-ctr-offered"))
                .andExpect(jsonPath("$.signedContentHash").doesNotExist());
    }

    private static ContractResponse legacyResponse() {
        return new ContractResponse("seed-ctr-offered", "listing-1", "buyer-1", "seller-1", "Coffee", "B", "S",
                "b@x.test", "s@x.test", null, "OFFERED");
    }

    private static UsernamePasswordAuthenticationToken buyer() {
        return new UsernamePasswordAuthenticationToken("buyer-1", null,
                List.of(new SimpleGrantedAuthority("ROLE_BUYER")));
    }
}
