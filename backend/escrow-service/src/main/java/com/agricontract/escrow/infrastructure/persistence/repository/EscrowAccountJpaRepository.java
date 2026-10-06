package com.agricontract.escrow.infrastructure.persistence.repository;

import com.agricontract.escrow.infrastructure.persistence.entity.EscrowAccountJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EscrowAccountJpaRepository extends JpaRepository<EscrowAccountJpaEntity, Long> {
    Optional<EscrowAccountJpaEntity> findByEscrowId(String escrowId);
    Optional<EscrowAccountJpaEntity> findByContractId(String contractId);
    boolean existsByContractId(String contractId);
}
