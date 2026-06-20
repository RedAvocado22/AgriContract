package com.agricontract.escrow.infrastructure.persistence.repository;

import com.agricontract.escrow.infrastructure.persistence.entity.EscrowDomainEventJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EscrowDomainEventJpaRepository extends JpaRepository<EscrowDomainEventJpaEntity, Long> {
    List<EscrowDomainEventJpaEntity> findByStatusOrderByCreatedAtAsc(EscrowDomainEventJpaEntity.Status status);
    boolean existsByEventId(String eventId);
}
