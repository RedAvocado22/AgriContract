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
    private String productName;     // snapshot at listing creation time
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

    /** Called from contract-service via OpenFeign when contract is SIGNED */
    public void close() { /* TODO */ }

    /** Called from scheduled job when deliveryDeadline has passed */
    public void expire() { /* TODO */ }
}
