package com.agricontract.product.domain.repository;

import com.agricontract.product.domain.model.Listing;
import com.agricontract.product.domain.model.vo.ListingId;
import com.agricontract.product.domain.model.vo.ListingStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ListingRepository {
    Listing save(Listing listing);
    Optional<Listing> findById(ListingId listingId);
    List<Listing> findByStatus(ListingStatus status);
    List<Listing> findBySellerId(String sellerId);
    List<Listing> findActiveExpiredBefore(LocalDate date);
}
