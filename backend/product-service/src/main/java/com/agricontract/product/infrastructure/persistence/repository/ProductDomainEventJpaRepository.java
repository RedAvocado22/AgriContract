package com.agricontract.product.infrastructure.persistence.repository;

import com.agricontract.product.infrastructure.persistence.entity.ProductDomainEventJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ProductDomainEventJpaRepository extends JpaRepository<ProductDomainEventJpaEntity, Long> {
    List<ProductDomainEventJpaEntity> findByStatusOrderByCreatedAtAsc(ProductDomainEventJpaEntity.Status status);

    boolean existsByEventId(String eventId);

    long countByStatusAndCreatedAtBefore(ProductDomainEventJpaEntity.Status status, LocalDateTime threshold);
}
