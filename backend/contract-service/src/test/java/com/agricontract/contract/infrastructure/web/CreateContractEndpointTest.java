package com.agricontract.contract.infrastructure.web;

import static com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.input;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agricontract.contract.application.dto.CoffeeContractView;
import com.agricontract.contract.application.dto.CreateContractCommand;
import com.agricontract.contract.application.exception.ListingServiceUnavailableException;
import com.agricontract.contract.application.usecase.*;
import com.agricontract.contract.domain.exception.ContractConflictException;
import com.agricontract.contract.domain.model.vo.ContractStatus;
import com.agricontract.contract.infrastructure.terms.JcsTermsCanonicalizer;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CreateContractEndpointTest {
    private static final String LISTING = "11111111-1111-1111-1111-111111111111";
    private static final String TOKEN = "2026-11-10T06:00:00.123456Z";
    private static final String TERMS_JSON = "{\"agreedPrice\":\"95000.00\"}";

    private final JcsTermsCanonicalizer canonicalizer = new JcsTermsCanonicalizer();
    private final CreateContractUseCase useCase = mock(CreateContractUseCase.class);
    private MockMvc mvc;
    private String terms;

    @BeforeEach
    void setUp() {
        ContractController controller = new ContractController(useCase, mock(NegotiateContractUseCase.class),
                mock(GetContractUseCase.class), mock(ListContractsUseCase.class),
                mock(UpdateContractTermsUseCase.class), canonicalizer);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        terms = canonicalizer.canonicalInputJson(input());
        when(useCase.execute(any())).thenReturn(new CreateContractUseCase.Result(new CoffeeContractView(
                UUID.randomUUID().toString(), LISTING, new BigDecimal("2500.5"), "buyer-1", "seller-1",
                ContractStatus.OFFERED, TERMS_JSON, "c".repeat(64)), false));
    }

    @Test
    void createsTheOffer_andAnswersTheUnwrappedContractResponse() throws Exception {
        UUID key = UUID.randomUUID();
        mvc.perform(request(body(terms), "BUYER").header("Idempotency-Key", key.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OFFERED"))
                .andExpect(jsonPath("$.reservedQuantity").value(2500.5))
                .andExpect(jsonPath("$.terms.agreedPrice").value("95000.00"))
                .andExpect(jsonPath("$.data").doesNotExist());

        ArgumentCaptor<CreateContractCommand> command = ArgumentCaptor.forClass(CreateContractCommand.class);
        verify(useCase).execute(command.capture());
        assertThat(command.getValue().buyerId()).isEqualTo("buyer-1");
        assertThat(command.getValue().idempotencyKey()).isEqualTo(key);
        assertThat(command.getValue().listingId()).isEqualTo(LISTING);
        assertThat(command.getValue().listingVersionToken()).isEqualTo(TOKEN);
        assertThat(canonicalizer.canonicalInputJson(command.getValue().terms())).isEqualTo(terms);
    }

    @Test
    void clientCannotSendAQuantity_goodsTerms_orAContractId() throws Exception {
        List<String> bad = List.of(
                body(terms).replaceFirst("^\\{", "{\"requestedQuantity\":10,"),
                body(terms).replaceFirst("^\\{", "{\"contractId\":\"" + UUID.randomUUID() + "\","),
                body(terms.replaceFirst("^\\{", "{\"goodsTerms\":{},")),
                "{\"listingId\":\"" + LISTING + "\",\"terms\":" + terms + "}");
        for (String json : bad) {
            mvc.perform(request(json, "BUYER").header("Idempotency-Key", UUID.randomUUID().toString()))
                    .andExpect(status().isBadRequest());
        }
        verify(useCase, never()).execute(any());
    }

    @Test
    void onlyABuyerCanOffer() throws Exception {
        mvc.perform(request(body(terms), "SELLER").header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
        verify(useCase, never()).execute(any());
    }

    @Test
    void idempotencyKeyIsRequired() throws Exception {
        mvc.perform(request(body(terms), "BUYER")).andExpect(status().isBadRequest());
        verify(useCase, never()).execute(any());
    }

    @Test
    void insufficientQuantityIsA409WithTheProductServiceCode() throws Exception {
        when(useCase.execute(any())).thenThrow(
                new ContractConflictException("INSUFFICIENT_LISTING_QUANTITY", "Only 100 kg left"));

        mvc.perform(request(body(terms), "BUYER").header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.startsWith(
                        "INSUFFICIENT_LISTING_QUANTITY")));
    }

    @Test
    void productServiceDownIsA503() throws Exception {
        when(useCase.execute(any())).thenThrow(new ListingServiceUnavailableException("down", null));

        mvc.perform(request(body(terms), "BUYER").header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isServiceUnavailable());
    }

    private static String body(String termsJson) {
        return "{\"listingId\":\"" + LISTING + "\",\"listingVersionToken\":\"" + TOKEN + "\",\"terms\":"
                + termsJson + "}";
    }

    private static MockHttpServletRequestBuilder request(String json, String role) {
        return post("/api/v1/contracts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .principal(new UsernamePasswordAuthenticationToken(role.equals("BUYER") ? "buyer-1" : "user-1",
                        null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
