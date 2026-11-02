package com.agricontract.product.infrastructure.web;

import com.agricontract.product.application.dto.ListingResponse;
import com.agricontract.product.application.dto.ListingSearchItem;
import com.agricontract.product.application.dto.ListingSearchQuery;
import com.agricontract.product.application.dto.ListingSearchResult;
import com.agricontract.product.application.dto.PagedResult;
import com.agricontract.product.application.dto.ReputationStatus;
import com.agricontract.product.application.dto.ReputationViewer;
import com.agricontract.product.application.dto.SellerReputation;
import com.agricontract.product.application.usecase.CloseListingUseCase;
import com.agricontract.product.application.usecase.CreateListingUseCase;
import com.agricontract.product.application.usecase.GetListingUseCase;
import com.agricontract.product.application.usecase.GetSellerListingsUseCase;
import com.agricontract.product.application.usecase.ListActiveListingsUseCase;
import com.agricontract.product.common.exception.ReputationSignInRequiredException;
import com.agricontract.product.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boundary of searchListings: public, viewer from the authenticated principal only, the listing fields unchanged
 * with sellerReputation beside them, and reputationStatus on the page.
 */
@WebMvcTest(ListingController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {"gateway.internal-secret=gw-secret", "service.internal-secret=svc-secret"})
class ListingSearchControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private ListActiveListingsUseCase listActiveListingsUseCase;
    @MockBean private GetListingUseCase getListingUseCase;
    @MockBean private CloseListingUseCase closeListingUseCase;
    @MockBean private CreateListingUseCase createListingUseCase;
    @MockBean private GetSellerListingsUseCase getSellerListingsUseCase;

    private static ListingSearchResult result(SellerReputation reputation, ReputationStatus status) {
        ListingResponse listing = ListingResponse.builder().listingId("listing-1").sellerId("seller-1")
                .productName("Ca phe Robusta").listingVersionToken("token-1").build();
        return new ListingSearchResult(new PagedResult<>(List.of(new ListingSearchItem(listing, reputation)), 1, 1),
                status);
    }

    private ListingSearchQuery captured() {
        ArgumentCaptor<ListingSearchQuery> query = ArgumentCaptor.forClass(ListingSearchQuery.class);
        verify(listActiveListingsUseCase).execute(query.capture());
        return query.getValue();
    }

    @Test
    void anonymous_searchesWithoutViewer() throws Exception {
        when(listActiveListingsUseCase.execute(any()))
                .thenReturn(result(null, ReputationStatus.SIGN_IN_REQUIRED));

        mockMvc.perform(get("/api/v1/listings").header("X-Gateway-Secret", "gw-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].listingId").value("listing-1"))
                .andExpect(jsonPath("$.data.content[0].sellerReputation").isEmpty())
                .andExpect(jsonPath("$.data.reputationStatus").value("SIGN_IN_REQUIRED"))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        assertThat(captured().viewer()).isNull();
    }

    @Test
    void signedIn_viewerComesFromThePrincipal_andReputationSitsBesideTheListingFields() throws Exception {
        when(listActiveListingsUseCase.execute(any()))
                .thenReturn(result(new SellerReputation(88, false), ReputationStatus.AVAILABLE));

        mockMvc.perform(get("/api/v1/listings").param("minSellerScore", "80").param("size", "10")
                        .header("X-Gateway-Secret", "gw-secret")
                        .header("X-User-Id", "buyer-1").header("X-User-Role", "BUYER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].listingId").value("listing-1"))
                .andExpect(jsonPath("$.data.content[0].listingVersionToken").value("token-1"))
                .andExpect(jsonPath("$.data.content[0].listing").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].sellerReputation.score").value(88))
                .andExpect(jsonPath("$.data.content[0].sellerReputation.locked").value(false))
                .andExpect(jsonPath("$.data.content[0].sellerReputation.lockHistory").doesNotExist())
                .andExpect(jsonPath("$.data.reputationStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10));

        ListingSearchQuery query = captured();
        assertThat(query.viewer()).isEqualTo(new ReputationViewer("buyer-1", List.of("BUYER")));
        assertThat(query.minSellerScore()).isEqualTo(80);
    }

    @Test
    void reputationOutage_isStill200() throws Exception {
        when(listActiveListingsUseCase.execute(any())).thenReturn(result(null, ReputationStatus.UNAVAILABLE));

        mockMvc.perform(get("/api/v1/listings").param("minSellerScore", "80")
                        .header("X-Gateway-Secret", "gw-secret")
                        .header("X-User-Id", "buyer-1").header("X-User-Role", "BUYER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].listingId").value("listing-1"))
                .andExpect(jsonPath("$.data.reputationStatus").value("UNAVAILABLE"));
    }

    @Test
    void anonymous_reputationFilter_is401() throws Exception {
        when(listActiveListingsUseCase.execute(any())).thenThrow(new ReputationSignInRequiredException());

        mockMvc.perform(get("/api/v1/listings").param("minSellerScore", "80")
                        .header("X-Gateway-Secret", "gw-secret"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void minSellerScore_outOfRange_is400() throws Exception {
        mockMvc.perform(get("/api/v1/listings").param("minSellerScore", "101")
                        .header("X-Gateway-Secret", "gw-secret"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/listings").param("minSellerScore", "-1")
                        .header("X-Gateway-Secret", "gw-secret"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(listActiveListingsUseCase);
    }
}
