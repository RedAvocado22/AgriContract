package com.agricontract.product.infrastructure.web;

import com.agricontract.product.application.dto.ListingContractSnapshotResponse;
import com.agricontract.product.application.dto.ListingContractSnapshotResponse.OriginSnapshot;
import com.agricontract.product.application.dto.ListingContractSnapshotResponse.PlotRef;
import com.agricontract.product.application.usecase.GetListingContractSnapshotUseCase;
import com.agricontract.product.common.exception.ListingNotFoundException;
import com.agricontract.product.common.exception.ReservationConflictException;
import com.agricontract.product.domain.model.vo.Commodity;
import com.agricontract.product.domain.model.vo.GeometryType;
import com.agricontract.product.domain.model.vo.VerificationLevel;
import com.agricontract.product.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Boundary of the internal snapshot read: service-secret only, bare body, frozen 409 codes, token required. */
@WebMvcTest(ListingContractSnapshotController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {"gateway.internal-secret=gw-secret", "service.internal-secret=svc-secret"})
class ListingContractSnapshotControllerTest {

    private static final String URL = "/internal/v1/listings/listing-1/contract-snapshot";

    @Autowired private MockMvc mockMvc;
    @MockBean private GetListingContractSnapshotUseCase useCase;

    @Test
    void withoutInternalSecret_is401_evenForAGatewayUser() throws Exception {
        mockMvc.perform(get(URL).param("listingVersionToken", "t")
                        .header("X-Gateway-Secret", "gw-secret")
                        .header("X-User-Id", "buyer-1").header("X-User-Role", "BUYER"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL).param("listingVersionToken", "t").header("X-Internal-Secret", "wrong"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(useCase);
    }

    @Test
    void currentToken_is200_withBareSnapshot() throws Exception {
        when(useCase.execute("listing-1", "t")).thenReturn(new ListingContractSnapshotResponse(Commodity.COFFEE,
                "listing-1", "product-1", "category-1", "Ca phe nhan", "Ca phe Robusta", "KG", null, null,
                new OriginSnapshot("Buon Ma Thuot, Dak Lak", List.of(new PlotRef("plot-1", "Ho Nguyen Van A",
                        GeometryType.POINT, "a".repeat(64), VerificationLevel.SELF_DECLARED)))));

        mockMvc.perform(get(URL).param("listingVersionToken", "t").header("X-Internal-Secret", "svc-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.commodity").value("COFFEE"))
                .andExpect(jsonPath("$.categoryName").value("Ca phe nhan"))
                .andExpect(jsonPath("$.cropYear").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.originSnapshot.plotRefs[0].geometryType").value("POINT"));
    }

    @Test
    void staleToken_is409_withFrozenCode() throws Exception {
        when(useCase.execute(anyString(), anyString())).thenThrow(new ReservationConflictException(
                ReservationConflictException.LISTING_VERSION_MISMATCH, "changed"));

        mockMvc.perform(get(URL).param("listingVersionToken", "old").header("X-Internal-Secret", "svc-secret"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LISTING_VERSION_MISMATCH"))
                .andExpect(jsonPath("$.path").value(URL));
    }

    @Test
    void unknownListing_is404() throws Exception {
        when(useCase.execute(anyString(), anyString())).thenThrow(new ListingNotFoundException("listing-1"));

        mockMvc.perform(get(URL).param("listingVersionToken", "t").header("X-Internal-Secret", "svc-secret"))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingToken_is400() throws Exception {
        mockMvc.perform(get(URL).header("X-Internal-Secret", "svc-secret"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(useCase);
    }
}
