package com.agricontract.product.infrastructure.persistence.repository;

import com.agricontract.product.infrastructure.persistence.entity.ListingEditKeyJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ListingEditKeyJpaRepository extends JpaRepository<ListingEditKeyJpaEntity, Long> {
    Optional<ListingEditKeyJpaEntity> findBySellerIdAndIdempotencyKey(String sellerId, String idempotencyKey);
}
