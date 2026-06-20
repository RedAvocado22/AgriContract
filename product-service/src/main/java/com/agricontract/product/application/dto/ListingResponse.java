package com.agricontract.product.application.dto;

import com.agricontract.product.domain.model.vo.ListingStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
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
        ListingStatus status
) {
}
