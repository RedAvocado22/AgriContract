package com.agricontract.product.infrastructure.persistence.repository;

import com.agricontract.product.domain.model.Listing;
import com.agricontract.product.domain.model.vo.ListingId;
import com.agricontract.product.domain.model.vo.ListingStatus;
import com.agricontract.product.domain.repository.ListingRepository;
import com.agricontract.product.infrastructure.persistence.mapper.ListingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ListingRepositoryImpl implements ListingRepository {

    private final ListingJpaRepository jpaRepository;
    private final ListingMapper mapper;

    @Override
    public Listing save(Listing listing) {
        return mapper.toDomain(jpaRepository.save(mapper.toJpaEntity(listing)));
    }

    @Override
    public Optional<Listing> findById(ListingId listingId) {
        return jpaRepository.findByListingId(listingId.value()).map(mapper::toDomain);
    }

    @Override
    public List<Listing> findByStatus(ListingStatus status) {
        return jpaRepository.findByStatus(status).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Listing> findBySellerId(String sellerId) {
        return jpaRepository.findBySellerId(sellerId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Listing> findActiveExpiredBefore(LocalDate date) {
        return jpaRepository.findActiveExpiredBefore(date).stream().map(mapper::toDomain).toList();
    }
}
