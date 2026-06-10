package com.agricontract.contract.infrastructure.feign.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ListingResponse(
        String listingId,
        String sellerId,
        String productId,
        String productName,    // snapshot field
        BigDecimal quantity,
        String quantityUnit,
        BigDecimal priceFloor,
        String currency,
        LocalDate deliveryDeadline,
        String status          // "ACTIVE" | "CLOSED" | "EXPIRED"
) {
}