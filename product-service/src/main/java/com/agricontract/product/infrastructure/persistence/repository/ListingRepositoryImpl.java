package com.agricontract.product.infrastructure.persistence.repository;

import com.agricontract.product.domain.model.Listing;
import com.agricontract.product.domain.model.vo.ListingId;
import com.agricontract.product.domain.model.vo.ListingStatus;
import com.agricontract.product.domain.repository.ListingRepository;
import com.agricontract.product.infrastructure.persistence.entity.ListingJpaEntity;
import com.agricontract.product.infrastructure.persistence.mapper.ListingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ListingRepositoryImpl implements ListingRepository {
    private final ListingMapper listingMapper;
    private final ListingJpaRepository listingJpaRepository;

    @Override
    public Listing save(Listing listing) {
        ListingJpaEntity entity = listingMapper.toJpaEntity(listing);
        ListingJpaEntity saved = listingJpaRepository.save(entity);
        return listingMapper.toDomain(saved);
    }

    @Override
    public Optional<Listing> findById(ListingId listingId) {
        return listingJpaRepository.findByListingId(listingId.value())
                .map(listingMapper::toDomain);
    }

    @Override
    public Page<Listing> findByStatus(ListingStatus status, Pageable pageable) {
        return listingJpaRepository.findByStatus(status, pageable)
                .map(listingMapper::toDomain);
    }

    @Override
    public Page<Listing> findBySellerId(String sellerId, Pageable pageable) {
        return listingJpaRepository.findBySellerId(sellerId, pageable)
                .map(listingMapper::toDomain);
    }

    @Override
    public List<Listing> findActiveExpiredBefore(LocalDate date) {
        return listingJpaRepository
                .findByStatusAndDeliveryDeadlineBefore(ListingStatus.ACTIVE, date).stream()
                .map(listingMapper::toDomain)
                .toList();
    }

}
