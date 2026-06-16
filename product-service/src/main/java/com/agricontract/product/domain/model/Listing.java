package com.agricontract.product.domain.model;

import com.agricontract.product.domain.model.vo.*;
import lombok.Getter;

import java.time.Instant;
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
    private Instant createdAt;
    private Instant updatedAt;

    private Listing() {}

    public static Listing create(String listingId, String sellerId,
                                  String productId, String productName,
                                  Quantity quantity, Money priceFloor,
                                  LocalDate deliveryDeadline) {
        Listing l = new Listing();
        l.listingId = new ListingId(listingId);
        l.sellerId = sellerId;
        l.productId = new ProductId(productId);
        l.productName = productName;
        l.quantity = quantity;
        l.priceFloor = priceFloor;
        l.deliveryDeadline = deliveryDeadline;
        l.status = ListingStatus.ACTIVE;
        l.createdAt = l.updatedAt = Instant.now();
        return l;
    }

    public static Listing reconstitute(String listingId, String sellerId,
                                        String productId, String productName,
                                        Quantity quantity, Money priceFloor,
                                        LocalDate deliveryDeadline, ListingStatus status) {
        Listing l = new Listing();
        l.listingId = new ListingId(listingId);
        l.sellerId = sellerId;
        l.productId = new ProductId(productId);
        l.productName = productName;
        l.quantity = quantity;
        l.priceFloor = priceFloor;
        l.deliveryDeadline = deliveryDeadline;
        l.status = status;
        return l;
    }

    /** Called from contract-service via OpenFeign when contract is SIGNED */
    public void close() {
        if (this.status != ListingStatus.ACTIVE)
            throw new IllegalStateException("Cannot close listing with status: " + status);
        this.status = ListingStatus.CLOSED;
        this.updatedAt = Instant.now();
    }

    /** Called from scheduled job when deliveryDeadline has passed */
    public void expire() {
        if (this.status != ListingStatus.ACTIVE)
            throw new IllegalStateException("Cannot expire listing with status: " + status);
        this.status = ListingStatus.EXPIRED;
        this.updatedAt = Instant.now();
    }
}
