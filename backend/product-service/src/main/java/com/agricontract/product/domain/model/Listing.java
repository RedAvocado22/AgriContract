package com.agricontract.product.domain.model;

import com.agricontract.product.domain.model.vo.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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
    // null = legacy listing created before inventory tracking: every reserve fails closed
    private BigDecimal quantityAvailable;
    // null = no seller minimum per contract
    private BigDecimal minOrderQuantity;
    private Money priceFloor;
    private LocalDate deliveryDeadline;
    private ListingStatus status;
    // assigned by persistence; null until the listing is first saved
    private Instant updatedAt;
    // copies of the registry plots the goods come from, taken at creation (design §2.4); empty only for legacy rows
    private List<ListingPlot> plots;
    // the coffee goods a contract will snapshot; fields are null on legacy rows
    private ListingGoods goods;

    private Listing() {
    }

    public static Listing reconstruct(ListingId listingId, String sellerId,
                                      ProductId productId, String productName, String coverImageUrl,
                                      Quantity quantity, BigDecimal quantityAvailable, BigDecimal minOrderQuantity,
                                      Money priceFloor, LocalDate deliveryDeadline, ListingStatus status,
                                      Instant updatedAt, List<ListingPlot> plots, ListingGoods goods) {
        Listing listing = new Listing();
        listing.listingId = listingId;
        listing.sellerId = sellerId;
        listing.productId = productId;
        listing.productName = productName;
        listing.coverImageUrl = coverImageUrl;
        listing.quantity = quantity;
        listing.quantityAvailable = quantityAvailable;
        listing.minOrderQuantity = minOrderQuantity;
        listing.priceFloor = priceFloor;
        listing.deliveryDeadline = deliveryDeadline;
        listing.status = status;
        listing.updatedAt = updatedAt;
        listing.plots = List.copyOf(plots);
        listing.goods = goods;

        return listing;
    }

    /**
     * coverImageUrl is snapshotted once here, same as productName — never re-synced
     * if the seller later updates the product's image list (audit trail for disputes).
     * Every new listing is coffee and must name all plots the goods come from: EUDR forbids mass balance (design §5).
     */
    public static Listing create(ListingId listingId, String sellerId,
                                 ProductId productId, String productName, String coverImageUrl,
                                 Quantity quantity, BigDecimal minOrderQuantity, Money priceFloor,
                                 LocalDate deliveryDeadline, List<ListingPlot> plots, ListingGoods goods) {
        if (minOrderQuantity != null
                && (minOrderQuantity.signum() <= 0 || minOrderQuantity.compareTo(quantity.value()) > 0)) {
            throw new IllegalArgumentException("minOrderQuantity must be positive and not exceed quantity");
        }
        if (plots == null || plots.isEmpty()) {
            throw new IllegalArgumentException("A coffee listing must reference at least one plot");
        }
        if (plots.stream().map(ListingPlot::sourceRegistryEntryId).distinct().count() != plots.size()) {
            throw new IllegalArgumentException("A plot can be referenced only once per listing");
        }
        requirePhase2Goods(goods);

        Listing listing = new Listing();
        listing.listingId = listingId;
        listing.sellerId = sellerId;
        listing.productId = productId;
        listing.productName = productName;
        listing.coverImageUrl = coverImageUrl;
        listing.quantity = quantity;
        listing.quantityAvailable = quantity.value();
        listing.minOrderQuantity = minOrderQuantity;
        listing.priceFloor = priceFloor;
        listing.deliveryDeadline = deliveryDeadline;
        listing.status = ListingStatus.ACTIVE;
        listing.plots = List.copyOf(plots);
        listing.goods = goods;

        return listing;
    }

    // A new listing must carry what a contract goods snapshot needs (design §8b, matrix 22).
    private static void requirePhase2Goods(ListingGoods goods) {
        if (goods == null || goods.categoryId() == null) {
            throw new IllegalArgumentException("A listing needs the category of its product");
        }
        if (goods.commodity() != Commodity.COFFEE) {
            throw new IllegalArgumentException("Phase 2 accepts COFFEE listings only");
        }
        if (goods.regionText() == null || goods.regionText().isBlank()) {
            throw new IllegalArgumentException("regionText must not be blank");
        }
        if (goods.declaredQualitySpec() == null) {
            throw new IllegalArgumentException("A coffee listing must declare its quality spec");
        }
    }

    // Legacy rows may have no quality spec until their next edit or republish (contract ListingResponse).
    public boolean isLegacyQualitySpecMissing() {
        return goods == null || goods.declaredQualitySpec() == null;
    }

    /**
     * Opaque optimistic-concurrency token: canonical UTC form of updatedAt.
     * Clients echo it back on reserve; inventory changes do not alter it.
     */
    public String getVersionToken() {
        return updatedAt == null ? null : updatedAt.toString();
    }

    /**
     * Explains why an atomic reserve matched no row, re-checked against the latest committed state.
     * Quantity problems win over state/version so a buyer losing the last stock sees "insufficient".
     */
    public ReservationRejection classifyRejectedReserve(BigDecimal requestedQuantity, String versionToken) {
        if (quantityAvailable == null
                || quantityAvailable.compareTo(requestedQuantity) < 0
                || (minOrderQuantity != null && minOrderQuantity.compareTo(requestedQuantity) > 0)) {
            return ReservationRejection.INSUFFICIENT_QUANTITY;
        }
        if (status != ListingStatus.ACTIVE) {
            return ReservationRejection.LISTING_STATE_CONFLICT;
        }
        // also covers a token that went stale between the failed update and this re-read
        return ReservationRejection.VERSION_MISMATCH;
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
