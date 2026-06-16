package com.agricontract.product.infrastructure.persistence.repository;

import com.agricontract.product.domain.model.vo.ListingStatus;
import com.agricontract.product.infrastructure.persistence.entity.ListingJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ListingJpaRepository extends JpaRepository<ListingJpaEntity, Long> {

    Optional<ListingJpaEntity> findByListingId(String listingId);

    List<ListingJpaEntity> findBySellerId(String sellerId);

    List<ListingJpaEntity> findByStatus(ListingStatus status);

    @Query("SELECT l FROM ListingJpaEntity l WHERE l.status = 'ACTIVE' AND l.deliveryDeadline < :date")
    List<ListingJpaEntity> findActiveExpiredBefore(@Param("date") LocalDate date);
}
