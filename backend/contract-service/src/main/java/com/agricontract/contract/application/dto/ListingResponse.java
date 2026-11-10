package com.agricontract.contract.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ListingResponse(
        String listingId,
        String sellerId,
        String productId,
        String productName,
        BigDecimal quantity,
        String quantityUnit,
        BigDecimal priceFloor,
        String currency,
        LocalDate deliveryDeadline,
        String status,
        /** Opaque version the buyer must have seen; compared with the one the offer echoes (#232). */
        String listingVersionToken
) {}
