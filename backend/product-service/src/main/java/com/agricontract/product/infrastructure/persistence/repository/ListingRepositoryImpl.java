package com.agricontract.product.infrastructure.persistence.repository;

import com.agricontract.product.common.exception.WriteConflictException;
import com.agricontract.product.domain.model.Listing;
import com.agricontract.product.domain.model.vo.CoffeeQualitySpec;
import com.agricontract.product.domain.model.vo.ListingGoods;
import com.agricontract.product.domain.model.vo.ListingId;
import com.agricontract.product.domain.model.vo.ListingPlot;
import com.agricontract.product.domain.model.vo.ListingStatus;
import com.agricontract.product.domain.repository.ListingRepository;
import com.agricontract.product.domain.repository.PageQuery;
import com.agricontract.product.infrastructure.persistence.entity.ListingJpaEntity;
import com.agricontract.product.infrastructure.persistence.entity.ListingPlotJpaEntity;
import com.agricontract.product.infrastructure.persistence.mapper.ListingMapper;
import com.agricontract.product.infrastructure.persistence.mapper.PlotRegistryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ListingRepositoryImpl implements ListingRepository {
    private static final List<ListingStatus> OPEN_FOR_RESERVATION =
            List.of(ListingStatus.AVAILABLE, ListingStatus.PARTIALLY_COMMITTED);

    private final ListingMapper listingMapper;
    private final ListingJpaRepository listingJpaRepository;
    private final ListingPlotJpaRepository listingPlotJpaRepository;
    private final PlotRegistryMapper plotRegistryMapper;

    @Override
    @Transactional // a self-call does not go through the proxy, so this needs its own annotation
    public Listing save(Listing listing) {
        return save(listing, null);
    }

    @Override
    @Transactional
    public Listing save(Listing listing, String createIdempotencyKey) {
        try {
            return persist(listing, createIdempotencyKey);
        } catch (DataIntegrityViolationException e) {
            throw new WriteConflictException("Listing " + listing.getListingId().value() + " could not be written", e);
        }
    }

    // Insert only: a listing changes afterwards through saveEdit and the inventory queries, never an entity save.
    private Listing persist(Listing listing, String createIdempotencyKey) {
        if (listingJpaRepository.findByListingId(listing.getListingId().value()).isPresent()) {
            throw new WriteConflictException("Listing " + listing.getListingId().value() + " already exists", null);
        }
        ListingJpaEntity entity = listingMapper.toJpaEntity(listing);
        entity.setCreateIdempotencyKey(createIdempotencyKey);

        // flush so @PrePersist has stamped updatedAt before the version token is read back
        ListingJpaEntity saved = listingJpaRepository.saveAndFlush(entity);
        insertPlots(saved.getListingId(), listing.getPlots());
        return toDomain(saved);
    }

    @Override
    public Optional<Listing> lockForEdit(ListingId listingId) {
        return listingJpaRepository.lockByListingId(listingId.value()).map(this::toDomain);
    }

    @Override
    public Listing saveEdit(Listing listing) {
        String listingId = listing.getListingId().value();
        ListingGoods goods = listing.getGoods();
        CoffeeQualitySpec spec = goods.declaredQualitySpec();
        int updated = listingJpaRepository.applyEdit(listingId,
                listing.getOfferedQuantity(),
                listing.getQuantityAvailable(),
                listing.getMinOrderQuantity(),
                listing.getAskingUnitPrice(),
                listing.getStatus(),
                listing.getCloseReason() == null ? null : listing.getCloseReason().name(),
                goods.regionText(),
                spec.type(),
                spec.moisturePercent(),
                spec.foreignMatterPercent(),
                spec.blackBrokenBeansPercent(),
                goods.packagingDeclaration(),
                goods.warehouseLocation(),
                goods.estimatedAvailabilityDate(),
                goods.traceabilityEvidencePackageId(),
                nextUpdatedAt(listing.getUpdatedAt()));
        if (updated != 1) {
            throw new IllegalStateException("Listing " + listingId + " was not locked for edit");
        }

        // Plots the seller kept keep their original copy; unticked ones go, newly ticked ones are copied now.
        Set<String> wanted = listing.getPlots().stream()
                .map(plot -> plot.sourceRegistryEntryId().value()).collect(Collectors.toSet());
        List<ListingPlotJpaEntity> stored = listingPlotJpaRepository
                .findByListingIdInOrderByListingIdAscHouseholdLabelAsc(List.of(listingId));
        Set<String> kept = new HashSet<>();
        for (ListingPlotJpaEntity plot : stored) {
            if (wanted.contains(plot.getSourceRegistryEntryId())) {
                kept.add(plot.getSourceRegistryEntryId());
            } else {
                listingPlotJpaRepository.delete(plot);
            }
        }
        listingPlotJpaRepository.flush();
        insertPlots(listingId, listing.getPlots().stream()
                .filter(plot -> !kept.contains(plot.sourceRegistryEntryId().value())).toList());

        return listingJpaRepository.findByListingId(listingId).map(this::toDomain).orElseThrow();
    }

    private void insertPlots(String listingId, List<ListingPlot> plots) {
        Instant declaredAt = Instant.now();
        listingPlotJpaRepository.saveAllAndFlush(plots.stream()
                .map(plot -> plotRegistryMapper.toJpaEntity(listingId, plot, declaredAt))
                .toList());
    }

    // The version token must change on every edit, even one landing in the same microsecond as the last write.
    private static LocalDateTime nextUpdatedAt(Instant previous) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        if (previous == null) {
            return now;
        }
        LocalDateTime floor = LocalDateTime.ofInstant(previous, ZoneOffset.UTC).plus(1, ChronoUnit.MICROS);
        return now.isBefore(floor) ? floor : now;
    }

    @Override
    public Optional<Listing> findById(ListingId listingId) {
        return listingJpaRepository.findByListingId(listingId.value())
                .map(this::toDomain);
    }

    @Override
    public Optional<Listing> findBySellerIdAndCreateIdempotencyKey(String sellerId, String createIdempotencyKey) {
        return listingJpaRepository.findBySellerIdAndCreateIdempotencyKey(sellerId, createIdempotencyKey)
                .map(this::toDomain);
    }

    @Override
    public List<Listing> findOpenForReservation(PageQuery page) {
        return toDomain(listingJpaRepository.findByStatusIn(OPEN_FOR_RESERVATION, toPageable(page)).getContent());
    }

    @Override
    public long countOpenForReservation() {
        return listingJpaRepository.countByStatusIn(OPEN_FOR_RESERVATION);
    }

    @Override
    public List<Listing> findBySellerId(String sellerId, PageQuery page) {
        return toDomain(listingJpaRepository.findBySellerId(sellerId, toPageable(page)).getContent());
    }

    @Override
    public long countBySellerId(String sellerId) {
        return listingJpaRepository.countBySellerId(sellerId);
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

    private static Pageable toPageable(PageQuery page) {
        if (page.sortBy() == null) {
            return PageRequest.of(page.page(), page.size());
        }
        Sort.Direction direction = page.descending() ? Sort.Direction.DESC : Sort.Direction.ASC;
        return PageRequest.of(page.page(), page.size(), Sort.by(direction, page.sortBy()));
    }

    private static void requirePositive(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }
}
