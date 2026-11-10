package com.agricontract.product.infrastructure.web;

import com.agricontract.product.application.usecase.CloseListingUseCase;
import com.agricontract.product.application.usecase.CreateListingUseCase;
import com.agricontract.product.application.usecase.GetListingUseCase;
import com.agricontract.product.application.usecase.GetSellerListingsUseCase;
import com.agricontract.product.application.usecase.ListActiveListingsUseCase;
import com.agricontract.product.common.exception.ActorNotEligibleException;
import com.agricontract.product.common.exception.EligibilityUnavailableException;
import com.agricontract.product.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boundary of CreateListing's eligibility gate (matrix 21f): user-service unavailable is 503, an ineligible seller
 * is 403, both through GlobalExceptionHandler on POST /api/v1/listings.
 */
@WebMvcTest(ListingController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {"gateway.internal-secret=gw-secret", "service.internal-secret=svc-secret"})
class CreateListingControllerTest {

    private static final String BODY = """
            {"productId":"product-1","quantity":1000,"quantityUnit":"KG","priceFloor":90000.00,"currency":"VND",
             "deliveryDeadline":"2099-01-15","registryEntryIds":["plot-1"],"regionText":"Buon Ma Thuot, Dak Lak",
             "declaredQualitySpec":{"commodity":"COFFEE","type":"ROBUSTA","moisturePercent":12.5,
              "foreignMatterPercent":0.5,"blackBrokenBeansPercent":3.0}}""";

    @Autowired private MockMvc mockMvc;
    @MockBean private GetListingUseCase getListingUseCase;
    @MockBean private CloseListingUseCase closeListingUseCase;
    @MockBean private ListActiveListingsUseCase listActiveListingsUseCase;
    @MockBean private CreateListingUseCase createListingUseCase;
    @MockBean private GetSellerListingsUseCase getSellerListingsUseCase;

    private ResultActions createAsSeller() throws Exception {
        return mockMvc.perform(post("/api/v1/listings")
                .header("X-Gateway-Secret", "gw-secret")
                .header("X-User-Id", "seller-1").header("X-User-Role", "SELLER")
                .contentType(MediaType.APPLICATION_JSON).content(BODY));
    }

    @Test
    void userServiceUnavailable_is503() throws Exception {
        when(createListingUseCase.execute(eq("seller-1"), any())).thenThrow(new EligibilityUnavailableException());

        createAsSeller()
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Eligibility check is temporarily unavailable"));
    }

    @Test
    void ineligibleSeller_is403() throws Exception {
        when(createListingUseCase.execute(eq("seller-1"), any())).thenThrow(new ActorNotEligibleException());

        createAsSeller()
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Account is not eligible for this action"));
    }
}
