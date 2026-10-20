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
     * Atomic conditional decrement of quantityAvailable. Fails closed (returns false) for legacy
     * listings with quantityAvailable = null and when the remaining stock is insufficient.
     */
    boolean decrementAvailable(ListingId listingId, BigDecimal quantity);

    /**
     * Atomic conditional restore of quantityAvailable; never exceeds the offered quantity.
     */
    boolean restoreAvailable(ListingId listingId, BigDecimal quantity);
}
