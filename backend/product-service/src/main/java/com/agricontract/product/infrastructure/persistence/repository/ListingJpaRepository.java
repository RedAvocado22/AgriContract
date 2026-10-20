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
import java.util.List;
import java.util.Optional;

public interface ListingJpaRepository extends JpaRepository<ListingJpaEntity, Long> {
    Optional<ListingJpaEntity> findByListingId(String listingId);

    Page<ListingJpaEntity> findBySellerId(String sellerId, Pageable pageable);
    long countBySellerId(String sellerId);

    Page<ListingJpaEntity> findByStatus(ListingStatus status, Pageable pageable);
    long countByStatus(ListingStatus status);

    List<ListingJpaEntity> findByStatusAndDeliveryDeadlineBefore(ListingStatus status, LocalDate date);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE ListingJpaEntity l
            SET l.quantityAvailable = l.quantityAvailable - :quantity
            WHERE l.listingId = :listingId
              AND l.quantityAvailable IS NOT NULL
              AND l.quantityAvailable >= :quantity
            """)
    int decrementAvailable(@Param("listingId") String listingId, @Param("quantity") BigDecimal quantity);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE ListingJpaEntity l
            SET l.quantityAvailable = l.quantityAvailable + :quantity
            WHERE l.listingId = :listingId
              AND l.quantityAvailable IS NOT NULL
              AND l.quantityAvailable + :quantity <= l.quantity
            """)
    int restoreAvailable(@Param("listingId") String listingId, @Param("quantity") BigDecimal quantity);
}
