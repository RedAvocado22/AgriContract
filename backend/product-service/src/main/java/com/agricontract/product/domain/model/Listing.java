package com.agricontract.product.domain.model;

import com.agricontract.product.domain.model.vo.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// Aggregate Root
// State machine (design §8b.1): AVAILABLE ⇄ PARTIALLY_COMMITTED ⇄ CLOSED, driven by reserve/release and seller edits.
// DRAFT and PAUSED belong to the contract but no code path produces them in this phase.
@Getter
public class Listing {

    // Every quantity is canonical kilograms (design §8b); the contract's unitOfMeasure is always KG.
    public static final String UNIT_OF_MEASURE = "KG";

    private ListingId listingId;
    private String sellerId;
    private ProductId productId;
    private String productName;
    private String coverImageUrl;
    private BigDecimal offeredQuantity;
    // null = legacy listing created before inventory tracking: every reserve fails closed until the next edit
    private BigDecimal quantityAvailable;
    // null only on legacy rows; required for every new or edited listing
    private BigDecimal minOrderQuantity;
    // VND per kg, exactly 2 decimals
    private BigDecimal askingUnitPrice;
    private ListingStatus status;
    // why a CLOSED listing is closed; decides whether a release may reopen it (design §8b.1)
    private CloseReason closeReason;
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
                                      BigDecimal offeredQuantity, BigDecimal quantityAvailable,
                                      BigDecimal minOrderQuantity, BigDecimal askingUnitPrice,
                                      ListingStatus status, CloseReason closeReason,
                                      Instant updatedAt, List<ListingPlot> plots, ListingGoods goods) {
        Listing listing = new Listing();
        listing.listingId = listingId;
        listing.sellerId = sellerId;
        listing.productId = productId;
        listing.productName = productName;
        listing.coverImageUrl = coverImageUrl;
        listing.offeredQuantity = offeredQuantity;
        listing.quantityAvailable = quantityAvailable;
        listing.minOrderQuantity = minOrderQuantity;
        listing.askingUnitPrice = askingUnitPrice;
        listing.status = status;
        listing.closeReason = closeReason;
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
                                 BigDecimal offeredQuantity, BigDecimal minOrderQuantity, BigDecimal askingUnitPrice,
                                 List<ListingPlot> plots, ListingGoods goods) {
        requireTerms(offeredQuantity, minOrderQuantity, askingUnitPrice);
        requirePlots(plots);
        requirePhase2Goods(goods);

        Listing listing = new Listing();
        listing.listingId = listingId;
        listing.sellerId = sellerId;
        listing.productId = productId;
        listing.productName = productName;
        listing.coverImageUrl = coverImageUrl;
        listing.offeredQuantity = offeredQuantity;
        listing.quantityAvailable = offeredQuantity;
        listing.minOrderQuantity = minOrderQuantity;
        listing.askingUnitPrice = askingUnitPrice;
        listing.status = ListingStatus.AVAILABLE;
        listing.plots = List.copyOf(plots);
        listing.goods = goods;

        return listing;
    }

    /**
     * Seller edit / republish (design §4 UpdateListing, §8b.1, D48). {@code reservedQuantity} is the sum of the
     * listing's reservations that are not RELEASED, read under the same lock as this edit. The offered quantity
     * cannot drop below it; what is left is reservable again. Lowering the offer to exactly the reserved total is how
     * a seller stops selling: the listing closes and a later release does not reopen it. A legacy row
     * (quantityAvailable null, no reservation) gets its inventory initialized here.
     */
    public void edit(BigDecimal offeredQuantity, BigDecimal minOrderQuantity, BigDecimal askingUnitPrice,
                     List<ListingPlot> plots, ListingGoods goods, BigDecimal reservedQuantity) {
        if (!isEditable()) {
            throw new IllegalArgumentException(
                    "A legacy listing without a coffee quality spec stays readable but cannot be edited");
        }
        requireTerms(offeredQuantity, minOrderQuantity, askingUnitPrice);
        requirePlots(plots);
        requirePhase2Goods(goods);
        if (reservedQuantity == null || reservedQuantity.signum() < 0) {
            throw new IllegalArgumentException("reservedQuantity must not be negative");
        }
        if (offeredQuantity.compareTo(reservedQuantity) < 0) {
            throw new IllegalArgumentException("offeredQuantity must not be below the reserved quantity");
        }

        this.offeredQuantity = offeredQuantity;
        this.minOrderQuantity = minOrderQuantity;
        this.askingUnitPrice = askingUnitPrice;
        this.plots = List.copyOf(plots);
        this.goods = goods;
        this.quantityAvailable = offeredQuantity.subtract(reservedQuantity);
        if (quantityAvailable.signum() == 0) {
            this.status = ListingStatus.CLOSED;
            this.closeReason = CloseReason.SELLER_CLOSED;
        } else {
            this.status = reservedQuantity.signum() == 0 ? ListingStatus.AVAILABLE : ListingStatus.PARTIALLY_COMMITTED;
            this.closeReason = null;
        }
    }

    /** Only a coffee listing with a quality spec can be edited into a Phase 2 listing (design §8b). */
    public boolean isEditable() {
        return goods != null && goods.commodity() == Commodity.COFFEE && goods.categoryId() != null
                && !isLegacyQualitySpecMissing();
    }

    public boolean isOwnedBy(String sellerId) {
        return this.sellerId.equals(sellerId);
    }

    private static void requireTerms(BigDecimal offeredQuantity, BigDecimal minOrderQuantity,
                                     BigDecimal askingUnitPrice) {
        if (offeredQuantity == null || offeredQuantity.signum() <= 0) {
            throw new IllegalArgumentException("offeredQuantity must be positive");
        }
        if (minOrderQuantity == null || minOrderQuantity.signum() <= 0
                || minOrderQuantity.compareTo(offeredQuantity) > 0) {
            throw new IllegalArgumentException("minOrderQuantity must be positive and not exceed offeredQuantity");
        }
        if (askingUnitPrice == null || askingUnitPrice.signum() <= 0) {
            throw new IllegalArgumentException("askingUnitPrice must be positive");
        }
    }

    private static void requirePlots(List<ListingPlot> plots) {
        if (plots == null || plots.isEmpty()) {
            throw new IllegalArgumentException("A coffee listing must reference at least one plot");
        }
        if (plots.stream().map(ListingPlot::sourceRegistryEntryId).distinct().count() != plots.size()) {
            throw new IllegalArgumentException("A plot can be referenced only once per listing");
        }
    }

    // A listing must carry what a contract goods snapshot needs (design §8b, matrix 22).
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
     * Clients echo it back on reserve; a seller edit changes it, inventory movement does not.
     */
    public String getVersionToken() {
        return updatedAt == null ? null : updatedAt.toString();
    }

    public boolean isAtVersion(String versionToken) {
        String current = getVersionToken();
        return current != null && current.equals(versionToken);
    }

    /**
     * Whether a contract goods snapshot can be built from this listing (golden-flow CoffeeGoodsTerms): coffee, with a
     * category, a region and at least one plot. Every listing created since Phase 2 is; legacy rows are not.
     */
    public boolean canBackCoffeeContract() {
        return goods != null
                && goods.commodity() == Commodity.COFFEE
                && goods.categoryId() != null
                && goods.regionText() != null && !goods.regionText().isBlank()
                && productName != null && !productName.isBlank()
                && !plots.isEmpty();
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
        if (!status.isOpenForReservation()) {
            return ReservationRejection.LISTING_STATE_CONFLICT;
        }
        // also covers a token that went stale between the failed update and this re-read
        return ReservationRejection.VERSION_MISMATCH;
    }
}
