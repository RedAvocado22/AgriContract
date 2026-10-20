package com.agricontract.product.domain.repository;

import com.agricontract.product.domain.model.Listing;
import com.agricontract.product.domain.model.vo.ListingId;
import com.agricontract.product.domain.model.vo.ListingStatus;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ListingRepository {
    Listing save(Listing listing);

    Optional<Listing> findById(ListingId listingId);

    List<Listing> findByStatus(ListingStatus status, Pageable pageable);
    long countByStatus(ListingStatus status);

    List<Listing> findBySellerId(String sellerId, Pageable pageable);
    long countBySellerId(String sellerId);

    List<Listing> findActiveExpiredBefore(LocalDate date);

    /**
     * Atomic conditional decrement of quantityAvailable. Applies only while the listing is ACTIVE, still at
     * {@code versionToken}, not legacy (quantityAvailable = null), has enough stock and the quantity meets
     * minOrderQuantity; a decrement to zero closes the listing as inventory-exhausted.
     *
     * @return false when any condition fails — nothing is changed
     */
    boolean reserveAvailable(ListingId listingId, BigDecimal quantity, String versionToken);

    /**
     * Atomic conditional restore of quantityAvailable; never exceeds the offered quantity. Reopens the listing
     * only if it was closed by inventory exhaustion — any other close (or expiry) is preserved.
     */
    boolean releaseAvailable(ListingId listingId, BigDecimal quantity);
}
