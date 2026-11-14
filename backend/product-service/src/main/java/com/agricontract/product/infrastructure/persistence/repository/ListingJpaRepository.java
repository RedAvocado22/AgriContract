package com.agricontract.product.infrastructure.persistence.repository;

import com.agricontract.product.domain.model.vo.CoffeeType;
import com.agricontract.product.domain.model.vo.ListingStatus;
import com.agricontract.product.infrastructure.persistence.entity.ListingJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

public interface ListingJpaRepository extends JpaRepository<ListingJpaEntity, Long> {
    Optional<ListingJpaEntity> findByListingId(String listingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM ListingJpaEntity l WHERE l.listingId = :listingId")
    Optional<ListingJpaEntity> lockByListingId(@Param("listingId") String listingId);

    Optional<ListingJpaEntity> findBySellerIdAndCreateIdempotencyKey(String sellerId, String createIdempotencyKey);

    Page<ListingJpaEntity> findBySellerId(String sellerId, Pageable pageable);
    long countBySellerId(String sellerId);

    Page<ListingJpaEntity> findByStatusIn(Collection<ListingStatus> statuses, Pageable pageable);
    long countByStatusIn(Collection<ListingStatus> statuses);

    // MySQL applies single-table SET assignments left to right on already-updated values,
    // so status/closeReason are computed before quantityAvailable changes.
    // updatedAt is not touched: inventory movement must not invalidate other buyers' version tokens.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE ListingJpaEntity l
            SET l.status = CASE WHEN l.quantityAvailable = :quantity
                                THEN com.agricontract.product.domain.model.vo.ListingStatus.CLOSED
                                ELSE com.agricontract.product.domain.model.vo.ListingStatus.PARTIALLY_COMMITTED END,
                l.closeReason = CASE WHEN l.quantityAvailable = :quantity
                                     THEN 'INVENTORY_EXHAUSTED'
                                     ELSE NULL END,
                l.quantityAvailable = l.quantityAvailable - :quantity
            WHERE l.listingId = :listingId
              AND l.status IN (com.agricontract.product.domain.model.vo.ListingStatus.AVAILABLE,
                               com.agricontract.product.domain.model.vo.ListingStatus.PARTIALLY_COMMITTED)
              AND l.updatedAt = :expectedUpdatedAt
              AND l.quantityAvailable IS NOT NULL
              AND l.quantityAvailable >= :quantity
              AND (l.minOrderQuantity IS NULL OR l.minOrderQuantity <= :quantity)
            """)
    int reserveAvailable(@Param("listingId") String listingId,
                         @Param("quantity") BigDecimal quantity,
                         @Param("expectedUpdatedAt") LocalDateTime expectedUpdatedAt);

    // quantityAvailable = quantity - reserved holds for every non-legacy row (D48), so the restored counter equal to
    // the offered quantity means nothing is reserved any more.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE ListingJpaEntity l
            SET l.status = CASE WHEN l.status IN (com.agricontract.product.domain.model.vo.ListingStatus.AVAILABLE,
                                                  com.agricontract.product.domain.model.vo.ListingStatus.PARTIALLY_COMMITTED)
                                  OR (l.status = com.agricontract.product.domain.model.vo.ListingStatus.CLOSED
                                      AND l.closeReason = 'INVENTORY_EXHAUSTED')
                                THEN (CASE WHEN l.quantityAvailable + :quantity = l.offeredQuantity
                                           THEN com.agricontract.product.domain.model.vo.ListingStatus.AVAILABLE
                                           ELSE com.agricontract.product.domain.model.vo.ListingStatus.PARTIALLY_COMMITTED END)
                                ELSE l.status END,
                l.closeReason = CASE WHEN l.closeReason = 'INVENTORY_EXHAUSTED'
                                     THEN NULL
                                     ELSE l.closeReason END,
                l.quantityAvailable = l.quantityAvailable + :quantity
            WHERE l.listingId = :listingId
              AND l.quantityAvailable IS NOT NULL
              AND l.quantityAvailable + :quantity <= l.offeredQuantity
            """)
    int releaseAvailable(@Param("listingId") String listingId, @Param("quantity") BigDecimal quantity);

    // One statement: the CHECKs quantity_available <= quantity and min_order_quantity <= quantity hold on the final
    // row, not on a half-applied one. Called only on a row locked by lockByListingId.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE ListingJpaEntity l
            SET l.offeredQuantity = :offeredQuantity,
                l.quantityAvailable = :quantityAvailable,
                l.minOrderQuantity = :minOrderQuantity,
                l.askingUnitPrice = :askingUnitPrice,
                l.status = :status,
                l.closeReason = :closeReason,
                l.regionText = :regionText,
                l.qualityType = :qualityType,
                l.qualityMoisturePercent = :moisturePercent,
                l.qualityForeignMatterPercent = :foreignMatterPercent,
                l.qualityBlackBrokenPercent = :blackBrokenPercent,
                l.packagingDeclaration = :packagingDeclaration,
                l.warehouseLocation = :warehouseLocation,
                l.estimatedAvailabilityDate = :estimatedAvailabilityDate,
                l.traceabilityEvidencePackageId = :traceabilityEvidencePackageId,
                l.updatedAt = :updatedAt
            WHERE l.listingId = :listingId
            """)
    int applyEdit(@Param("listingId") String listingId,
                  @Param("offeredQuantity") BigDecimal offeredQuantity,
                  @Param("quantityAvailable") BigDecimal quantityAvailable,
                  @Param("minOrderQuantity") BigDecimal minOrderQuantity,
                  @Param("askingUnitPrice") BigDecimal askingUnitPrice,
                  @Param("status") ListingStatus status,
                  @Param("closeReason") String closeReason,
                  @Param("regionText") String regionText,
                  @Param("qualityType") CoffeeType qualityType,
                  @Param("moisturePercent") BigDecimal moisturePercent,
                  @Param("foreignMatterPercent") BigDecimal foreignMatterPercent,
                  @Param("blackBrokenPercent") BigDecimal blackBrokenPercent,
                  @Param("packagingDeclaration") String packagingDeclaration,
                  @Param("warehouseLocation") String warehouseLocation,
                  @Param("estimatedAvailabilityDate") LocalDate estimatedAvailabilityDate,
                  @Param("traceabilityEvidencePackageId") String traceabilityEvidencePackageId,
                  @Param("updatedAt") LocalDateTime updatedAt);
}
