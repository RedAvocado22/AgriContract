package com.agricontract.contract.application.dto;

import java.math.BigDecimal;

/** product-service ListingReservationResponse (supplementary-services-api.yaml). */
public record ListingReservation(
        String contractId,
        String listingId,
        BigDecimal reservedQuantity,
        String state,
        BigDecimal quantityAvailable) {
}
