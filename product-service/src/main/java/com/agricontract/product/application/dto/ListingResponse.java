package com.agricontract.product.application.dto;

import com.agricontract.product.domain.model.Listing;
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
    public static ListingResponse from(Listing listing) {
        return ListingResponse.builder()
                .listingId(listing.getListingId().value())
                .sellerId(listing.getSellerId())
                .productId(listing.getProductId().value())
                .productName(listing.getProductName())
                .quantity(listing.getQuantity().value())
                .quantityUnit(listing.getQuantity().unit())
                .priceFloor(listing.getPriceFloor().amount())
                .currency(listing.getPriceFloor().currency())
                .deliveryDeadline(listing.getDeliveryDeadline())
                .status(listing.getStatus())
                .build();
    }
}
