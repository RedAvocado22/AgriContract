package com.agricontract.product.infrastructure.persistence.repository;

import com.agricontract.product.domain.model.Listing;
import com.agricontract.product.domain.model.vo.ListingId;
import com.agricontract.product.domain.model.vo.ListingStatus;
import com.agricontract.product.domain.repository.ListingRepository;
import com.agricontract.product.infrastructure.persistence.entity.ListingJpaEntity;
import com.agricontract.product.infrastructure.persistence.mapper.ListingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ListingRepositoryImpl implements ListingRepository {
    private final ListingMapper listingMapper;
    private final ListingJpaRepository listingJpaRepository;

    @Override
    @Transactional
    public Listing save(Listing listing) {
        ListingJpaEntity entity = listingJpaRepository
                .findByListingId(listing.getListingId().value())
                .map(existing -> {
                    // only syncs status — extend here if new mutable fields are added to Listing.
                    // quantityAvailable is deliberately not synced: it only changes via the conditional updates below.
                    existing.setStatus(listing.getStatus());
                    return existing;
                })
                .orElseGet(() -> listingMapper.toJpaEntity(listing));

        // flush so @PreUpdate has stamped updatedAt before the version token is read back
        ListingJpaEntity saved = listingJpaRepository.saveAndFlush(entity);
        return listingMapper.toDomain(saved);
    }

    @Override
    public Optional<Listing> findById(ListingId listingId) {
        return listingJpaRepository.findByListingId(listingId.value())
                .map(listingMapper::toDomain);
    }

    @Override
    public List<Listing> findByStatus(ListingStatus status, Pageable pageable) {
        return listingJpaRepository.findByStatus(status, pageable)
                .getContent().stream().map(listingMapper::toDomain).toList();
    }

    @Override
    public long countByStatus(ListingStatus status) {
        return listingJpaRepository.countByStatus(status);
    }

    @Override
    public List<Listing> findBySellerId(String sellerId, Pageable pageable) {
        return listingJpaRepository.findBySellerId(sellerId, pageable)
                .getContent().stream().map(listingMapper::toDomain).toList();
    }

    @Override
    public long countBySellerId(String sellerId) {
        return listingJpaRepository.countBySellerId(sellerId);
    }

    @Override
    public List<Listing> findActiveExpiredBefore(LocalDate date) {
        return listingJpaRepository
                .findByStatusAndDeliveryDeadlineBefore(ListingStatus.ACTIVE, date).stream()
                .map(listingMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public boolean reserveAvailable(ListingId listingId, BigDecimal quantity, String versionToken) {
        requirePositive(quantity);
        LocalDateTime expectedUpdatedAt;
        try {
            expectedUpdatedAt = ListingMapper.fromVersionToken(versionToken);
        } catch (DateTimeParseException | NullPointerException e) {
            return false; // a token this service never issued cannot match any version
        }
        return listingJpaRepository.reserveAvailable(listingId.value(), quantity, expectedUpdatedAt) == 1;
    }

    @Override
    @Transactional
    public boolean releaseAvailable(ListingId listingId, BigDecimal quantity) {
        requirePositive(quantity);
        return listingJpaRepository.releaseAvailable(listingId.value(), quantity) == 1;
    }

    private static void requirePositive(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }
}
