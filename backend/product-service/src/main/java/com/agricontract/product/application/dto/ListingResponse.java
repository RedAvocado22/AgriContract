package com.agricontract.product.application.dto;

import com.agricontract.product.domain.model.Listing;
import com.agricontract.product.domain.model.vo.ListingStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Builder
public record ListingResponse(
        String listingId,
        String sellerId,
        String productId,
        String productName,    // snapshot field
        String coverImageUrl,  // snapshot field
        BigDecimal quantity,
        String quantityUnit,
        BigDecimal quantityAvailable,  // server-owned, null for legacy listings
        BigDecimal minOrderQuantity,   // null = no seller minimum
        BigDecimal priceFloor,
        String currency,
        LocalDate deliveryDeadline,
        ListingStatus status,
        String listingVersionToken,    // opaque; contract-service echoes it to reserve
        List<String> registryEntryIds  // registry plots the goods come from; empty for legacy listings
) {
    public static ListingResponse from(Listing listing) {
        return ListingResponse.builder()
                .listingId(listing.getListingId().value())
                .sellerId(listing.getSellerId())
                .productId(listing.getProductId().value())
                .productName(listing.getProductName())
                .coverImageUrl(listing.getCoverImageUrl())
                .quantity(listing.getQuantity().value())
                .quantityUnit(listing.getQuantity().unit())
                .quantityAvailable(listing.getQuantityAvailable())
                .minOrderQuantity(listing.getMinOrderQuantity())
                .priceFloor(listing.getPriceFloor().amount())
                .currency(listing.getPriceFloor().currency())
                .deliveryDeadline(listing.getDeliveryDeadline())
                .status(listing.getStatus())
                .listingVersionToken(listing.getVersionToken())
                .registryEntryIds(listing.getPlots().stream().map(p -> p.sourceRegistryEntryId().value()).toList())
                .build();
    }
}
