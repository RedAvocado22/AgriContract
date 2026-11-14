package com.agricontract.product.domain.repository;

import com.agricontract.product.domain.model.Listing;
import com.agricontract.product.domain.model.vo.ListingId;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ListingRepository {
    /** Inserts a new listing; see {@link #save(Listing, String)}. */
    Listing save(Listing listing);

    /**
     * Inserts a new listing and records the createListing Idempotency-Key, unique per seller. A concurrent insert
     * with the same seller and key (or the same listingId) fails with
     * {@link com.agricontract.product.common.exception.WriteConflictException}.
     */
    Listing save(Listing listing, String createIdempotencyKey);

    Optional<Listing> findById(ListingId listingId);

    /** Reads and row-locks the listing until the caller's transaction ends (updateListing). */
    Optional<Listing> lockForEdit(ListingId listingId);

    /**
     * Writes a seller edit of a listing locked by {@link #lockForEdit}: terms, goods, plot copies, inventory, status
     * and close reason. updatedAt moves forward, so the listingVersionToken changes.
     */
    Listing saveEdit(Listing listing);

    Optional<Listing> findBySellerIdAndCreateIdempotencyKey(String sellerId, String createIdempotencyKey);

    /** Listings a buyer can still reserve from: AVAILABLE or PARTIALLY_COMMITTED. */
    List<Listing> findOpenForReservation(PageQuery page);
    long countOpenForReservation();

    List<Listing> findBySellerId(String sellerId, PageQuery page);
    long countBySellerId(String sellerId);

    /**
     * Atomic conditional decrement of quantityAvailable (design §8b.2). Applies only while the listing is AVAILABLE
     * or PARTIALLY_COMMITTED, still at {@code versionToken}, not legacy (quantityAvailable = null), has enough stock
     * and the quantity meets minOrderQuantity. The listing becomes PARTIALLY_COMMITTED, or CLOSED as
     * inventory-exhausted when nothing is left.
     *
     * @return false when any condition fails — nothing is changed
     */
    boolean reserveAvailable(ListingId listingId, BigDecimal quantity, String versionToken);

    /**
     * Atomic conditional restore of quantityAvailable; never exceeds the offered quantity. An open listing, or one
     * closed by inventory exhaustion, becomes AVAILABLE when nothing stays reserved and PARTIALLY_COMMITTED otherwise.
     * Any other close (seller, legacy) and PAUSED are kept: only the counter moves (design §8b.1).
     */
    boolean releaseAvailable(ListingId listingId, BigDecimal quantity);
}
