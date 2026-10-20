package com.agricontract.product.infrastructure.persistence.repository;

import com.agricontract.product.domain.model.vo.ListingStatus;
import com.agricontract.product.infrastructure.persistence.entity.ListingJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ListingJpaRepository extends JpaRepository<ListingJpaEntity, Long> {
    Optional<ListingJpaEntity> findByListingId(String listingId);

    Page<ListingJpaEntity> findBySellerId(String sellerId, Pageable pageable);
    long countBySellerId(String sellerId);

    Page<ListingJpaEntity> findByStatus(ListingStatus status, Pageable pageable);
    long countByStatus(ListingStatus status);

    List<ListingJpaEntity> findByStatusAndDeliveryDeadlineBefore(ListingStatus status, LocalDate date);

    // MySQL applies single-table SET assignments left to right on already-updated values,
    // so status/closeReason are computed before quantityAvailable changes.
    // updatedAt is not touched: inventory movement must not invalidate other buyers' version tokens.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE ListingJpaEntity l
            SET l.status = CASE WHEN l.quantityAvailable = :quantity
                                THEN com.agricontract.product.domain.model.vo.ListingStatus.CLOSED
                                ELSE l.status END,
                l.closeReason = CASE WHEN l.quantityAvailable = :quantity
                                     THEN 'INVENTORY_EXHAUSTED'
                                     ELSE l.closeReason END,
                l.quantityAvailable = l.quantityAvailable - :quantity
            WHERE l.listingId = :listingId
              AND l.status = com.agricontract.product.domain.model.vo.ListingStatus.ACTIVE
              AND l.updatedAt = :expectedUpdatedAt
              AND l.quantityAvailable IS NOT NULL
              AND l.quantityAvailable >= :quantity
              AND (l.minOrderQuantity IS NULL OR l.minOrderQuantity <= :quantity)
            """)
    int reserveAvailable(@Param("listingId") String listingId,
                         @Param("quantity") BigDecimal quantity,
                         @Param("expectedUpdatedAt") LocalDateTime expectedUpdatedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE ListingJpaEntity l
            SET l.status = CASE WHEN l.closeReason = 'INVENTORY_EXHAUSTED'
                                 AND l.status = com.agricontract.product.domain.model.vo.ListingStatus.CLOSED
                                THEN com.agricontract.product.domain.model.vo.ListingStatus.ACTIVE
                                ELSE l.status END,
                l.closeReason = CASE WHEN l.closeReason = 'INVENTORY_EXHAUSTED'
                                     THEN NULL
                                     ELSE l.closeReason END,
                l.quantityAvailable = l.quantityAvailable + :quantity
            WHERE l.listingId = :listingId
              AND l.quantityAvailable IS NOT NULL
              AND l.quantityAvailable + :quantity <= l.quantity
            """)
    int releaseAvailable(@Param("listingId") String listingId, @Param("quantity") BigDecimal quantity);
}
