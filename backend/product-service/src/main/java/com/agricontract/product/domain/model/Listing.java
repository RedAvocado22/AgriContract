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
    private String productName;
    private String coverImageUrl;
    private Quantity quantity;
    private Money priceFloor;
    private LocalDate deliveryDeadline;
    private ListingStatus status;

    private Listing() {
    }

    public static Listing reconstruct(ListingId listingId, String sellerId,
                                      ProductId productId, String productName, String coverImageUrl,
                                      Quantity quantity, Money priceFloor,
                                      LocalDate deliveryDeadline, ListingStatus status) {
        Listing listing = new Listing();
        listing.listingId = listingId;
        listing.sellerId = sellerId;
        listing.productId = productId;
        listing.productName = productName;
        listing.coverImageUrl = coverImageUrl;
        listing.quantity = quantity;
        listing.priceFloor = priceFloor;
        listing.deliveryDeadline = deliveryDeadline;
        listing.status = status;

        return listing;
    }

    /**
     * coverImageUrl is snapshotted once here, same as productName — never re-synced
     * if the seller later updates the product's image list (audit trail for disputes).
     */
    public static Listing create(ListingId listingId, String sellerId,
                                 ProductId productId, String productName, String coverImageUrl,
                                 Quantity quantity, Money priceFloor,
                                 LocalDate deliveryDeadline) {
        Listing listing = new Listing();
        listing.listingId = listingId;
        listing.sellerId = sellerId;
        listing.productId = productId;
        listing.productName = productName;
        listing.coverImageUrl = coverImageUrl;
        listing.quantity = quantity;
        listing.priceFloor = priceFloor;
        listing.deliveryDeadline = deliveryDeadline;
        listing.status = ListingStatus.ACTIVE;

        return listing;
    }

    /**
     * Called from contract-service via OpenFeign when contract is SIGNED
     */
    public void close() {
        if (this.status != ListingStatus.ACTIVE) {
            throw new IllegalStateException("Listing status must be ACTIVE");
        }
        this.status = ListingStatus.CLOSED;
    }

    /**
     * Called from scheduled job when deliveryDeadline has passed
     */
    public void expire() {
        if (this.status != ListingStatus.ACTIVE) {
            throw new IllegalStateException("Listing status must be ACTIVE");
        }
        this.status = ListingStatus.EXPIRED;
    }
}
