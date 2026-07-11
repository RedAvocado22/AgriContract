package com.agricontract.escrow.infrastructure.persistence.repository;

import com.agricontract.escrow.infrastructure.persistence.entity.EscrowTransactionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EscrowTransactionJpaRepository extends JpaRepository<EscrowTransactionJpaEntity, Long> {
    List<EscrowTransactionJpaEntity> findByEscrowId(String escrowId);
}
