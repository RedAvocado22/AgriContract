package com.agricontract.contract.infrastructure.persistence.repository;

import com.agricontract.contract.infrastructure.persistence.entity.ContractDomainEventJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ContractDomainEventJpaRepository extends JpaRepository<ContractDomainEventJpaEntity, Long> {
    List<ContractDomainEventJpaEntity> findByStatusOrderByCreatedAtAsc(ContractDomainEventJpaEntity.Status status);
    boolean existsByEventId(String eventId);
    long countByStatusAndCreatedAtBefore(ContractDomainEventJpaEntity.Status status, LocalDateTime threshold);
}
