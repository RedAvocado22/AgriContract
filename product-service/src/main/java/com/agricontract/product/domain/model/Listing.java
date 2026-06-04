package com.agricontract.product.domain.model;

import com.agricontract.product.domain.model.vo.*;
import lombok.Getter;

import java.time.LocalDate;

// Aggregate Root
// State machine: ACTIVE → CLOSED | EXPIRED
@Getter
public class Listing {

    private ListingId listingId;
    private String sellerId;
    private ProductId productId;
    private String productName;     // snapshot tại thời điểm tạo
    private Quantity quantity;
    private Money priceFloor;
    private LocalDate deliveryDeadline;
    private ListingStatus status;

    private Listing() {}

    public static Listing create(ListingId listingId, String sellerId,
                                  ProductId productId, String productName,
                                  Quantity quantity, Money priceFloor,
                                  LocalDate deliveryDeadline) {
        // TODO
        throw new UnsupportedOperationException("TODO");
    }

    /** Gọi từ contract-service qua OpenFeign khi contract SIGNED */
    public void close() { /* TODO */ }

    /** Gọi từ scheduled job khi deliveryDeadline qua */
    public void expire() { /* TODO */ }
}
