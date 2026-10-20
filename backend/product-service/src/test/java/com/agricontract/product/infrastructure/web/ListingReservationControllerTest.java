package com.agricontract.product.infrastructure.web;

import com.agricontract.product.application.dto.ListingReservationResponse;
import com.agricontract.product.application.dto.ReservationResult;
import com.agricontract.product.application.usecase.ReleaseListingReservationUseCase;
import com.agricontract.product.application.usecase.ReserveListingQuantityUseCase;
import com.agricontract.product.common.exception.ReservationConflictException;
import com.agricontract.product.domain.model.vo.ReservationStatus;
import com.agricontract.product.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boundary of the internal reservation API: service-secret only, 201 vs 200 replay,
 * frozen error-catalog code in the 409 envelope, request validation.
 */
@WebMvcTest(ListingReservationController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {"gateway.internal-secret=gw-secret", "service.internal-secret=svc-secret"})
class ListingReservationControllerTest {

    private static final String RESERVE_BODY = """
            {"contractId":"contract-1","listingId":"listing-1","requestedQuantity":40,"listingVersionToken":"t"}""";
    private static final String RELEASE_BODY = """
            {"listingId":"listing-1","reservedQuantity":40,"reason":"CREATE_PERSISTENCE_COMPENSATION"}""";

    @Autowired private MockMvc mockMvc;
    @MockBean private ReserveListingQuantityUseCase reserveUseCase;
    @MockBean private ReleaseListingReservationUseCase releaseUseCase;

    @Test
    void reserve_withoutInternalSecret_is401_evenWithGatewaySecret() throws Exception {
        mockMvc.perform(post("/internal/v1/listing-reservations")
                        .header("X-Gateway-Secret", "gw-secret")
                        .header("X-User-Id", "buyer-1").header("X-User-Role", "BUYER")
                        .contentType(MediaType.APPLICATION_JSON).content(RESERVE_BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/internal/v1/listing-reservations")
                        .header("X-Internal-Secret", "wrong")
                        .contentType(MediaType.APPLICATION_JSON).content(RESERVE_BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(reserveUseCase);
    }

    @Test
    void reserve_new_is201_withBareBody() throws Exception {
        when(reserveUseCase.execute(any())).thenReturn(result(true));

        mockMvc.perform(internal(post("/internal/v1/listing-reservations")).content(RESERVE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contractId").value("contract-1"))
                .andExpect(jsonPath("$.state").value("RESERVED"))
                .andExpect(jsonPath("$.quantityAvailable").value(60));
    }

    @Test
    void reserve_replay_is200() throws Exception {
        when(reserveUseCase.execute(any())).thenReturn(result(false));

        mockMvc.perform(internal(post("/internal/v1/listing-reservations")).content(RESERVE_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void reserve_conflict_is409_withFrozenCode() throws Exception {
        when(reserveUseCase.execute(any())).thenThrow(new ReservationConflictException(
                ReservationConflictException.INSUFFICIENT_LISTING_QUANTITY, "insufficient"));

        mockMvc.perform(internal(post("/internal/v1/listing-reservations")).content(RESERVE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_LISTING_QUANTITY"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.path").value("/internal/v1/listing-reservations"));
    }

    @Test
    void reserve_nonPositiveQuantity_is400() throws Exception {
        mockMvc.perform(internal(post("/internal/v1/listing-reservations")).content("""
                        {"contractId":"contract-1","listingId":"listing-1","requestedQuantity":0,"listingVersionToken":"t"}"""))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reserveUseCase);
    }

    @Test
    void release_is204() throws Exception {
        mockMvc.perform(internal(post("/internal/v1/listing-reservations/contract-1/release")).content(RELEASE_BODY))
                .andExpect(status().isNoContent());
        verify(releaseUseCase).execute(eq("contract-1"), any());
    }

    @Test
    void release_wrongReason_is400() throws Exception {
        mockMvc.perform(internal(post("/internal/v1/listing-reservations/contract-1/release")).content("""
                        {"listingId":"listing-1","reservedQuantity":40,"reason":"WITHDRAWN"}"""))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(releaseUseCase);
    }

    private static MockHttpServletRequestBuilder internal(MockHttpServletRequestBuilder request) {
        return request.header("X-Internal-Secret", "svc-secret").contentType(MediaType.APPLICATION_JSON);
    }

    private static ReservationResult result(boolean created) {
        return new ReservationResult(new ListingReservationResponse("contract-1", "listing-1",
                new BigDecimal("40"), ReservationStatus.RESERVED, new BigDecimal("60")), created);
    }
}
