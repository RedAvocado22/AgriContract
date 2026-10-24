package com.agricontract.product.infrastructure.persistence.repository;

import com.agricontract.product.domain.model.Listing;
import com.agricontract.product.domain.model.vo.ListingId;
import com.agricontract.product.domain.model.vo.ListingPlot;
import com.agricontract.product.domain.model.vo.ListingStatus;
import com.agricontract.product.domain.repository.ListingRepository;
import com.agricontract.product.infrastructure.persistence.entity.ListingJpaEntity;
import com.agricontract.product.infrastructure.persistence.entity.ListingPlotJpaEntity;
import com.agricontract.product.infrastructure.persistence.mapper.ListingMapper;
import com.agricontract.product.infrastructure.persistence.mapper.PlotRegistryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ListingRepositoryImpl implements ListingRepository {
    private final ListingMapper listingMapper;
    private final ListingJpaRepository listingJpaRepository;
    private final ListingPlotJpaRepository listingPlotJpaRepository;
    private final PlotRegistryMapper plotRegistryMapper;

    @Override
    @Transactional
    public Listing save(Listing listing) {
        Optional<ListingJpaEntity> current = listingJpaRepository.findByListingId(listing.getListingId().value());
        ListingJpaEntity entity = current
                .map(existing -> {
                    // only syncs status — extend here if new mutable fields are added to Listing.
                    // quantityAvailable is deliberately not synced: it only changes via the conditional updates below.
                    existing.setStatus(listing.getStatus());
                    return existing;
                })
                .orElseGet(() -> listingMapper.toJpaEntity(listing));

        // flush so @PreUpdate has stamped updatedAt before the version token is read back
        ListingJpaEntity saved = listingJpaRepository.saveAndFlush(entity);
        if (current.isEmpty()) {
            // plot copies are written once, with the listing, and never touched again
            Instant declaredAt = Instant.now();
            listingPlotJpaRepository.saveAllAndFlush(listing.getPlots().stream()
                    .map(plot -> plotRegistryMapper.toJpaEntity(saved.getListingId(), plot, declaredAt))
                    .toList());
        }
        return toDomain(saved);
    }

    @Override
    public Optional<Listing> findById(ListingId listingId) {
        return listingJpaRepository.findByListingId(listingId.value())
                .map(this::toDomain);
    }

    @Override
    public List<Listing> findByStatus(ListingStatus status, Pageable pageable) {
        return toDomain(listingJpaRepository.findByStatus(status, pageable).getContent());
    }

    @Override
    public long countByStatus(ListingStatus status) {
        return listingJpaRepository.countByStatus(status);
    }

    @Override
    public List<Listing> findBySellerId(String sellerId, Pageable pageable) {
        return toDomain(listingJpaRepository.findBySellerId(sellerId, pageable).getContent());
    }

    @Override
    public long countBySellerId(String sellerId) {
        return listingJpaRepository.countBySellerId(sellerId);
    }

    @Override
    public List<Listing> findActiveExpiredBefore(LocalDate date) {
        return toDomain(listingJpaRepository.findByStatusAndDeliveryDeadlineBefore(ListingStatus.ACTIVE, date));
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

    private Listing toDomain(ListingJpaEntity entity) {
        return toDomain(List.of(entity)).get(0);
    }

    // one plot query per page of listings, not one per listing
    private List<Listing> toDomain(List<ListingJpaEntity> entities) {
        Map<String, List<ListingPlot>> plots = plotsOf(entities.stream().map(ListingJpaEntity::getListingId).toList());
        return entities.stream()
                .map(entity -> listingMapper.toDomain(entity, plots.getOrDefault(entity.getListingId(), List.of())))
                .toList();
    }

    private Map<String, List<ListingPlot>> plotsOf(Collection<String> listingIds) {
        if (listingIds.isEmpty()) {
            return Map.of();
        }
        return listingPlotJpaRepository.findByListingIdInOrderByListingIdAscHouseholdLabelAsc(listingIds).stream()
                .collect(Collectors.groupingBy(ListingPlotJpaEntity::getListingId, LinkedHashMap::new,
                        Collectors.mapping(plotRegistryMapper::toDomain, Collectors.toList())));
    }

    private static void requirePositive(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }
}
