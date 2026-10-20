package com.agricontract.product.application.dto;

import com.agricontract.product.domain.model.Reservation;
import com.agricontract.product.domain.model.vo.ReservationStatus;

import java.math.BigDecimal;

public record ListingReservationResponse(
        String contractId,
        String listingId,
        BigDecimal reservedQuantity,
        ReservationStatus state,
        BigDecimal quantityAvailable
) {
    public static ListingReservationResponse from(Reservation reservation, BigDecimal quantityAvailable) {
        return new ListingReservationResponse(
                reservation.getContractId().value(),
                reservation.getListingId().value(),
                reservation.getReservedQuantity(),
                reservation.getStatus(),
                quantityAvailable);
    }
}
