package com.agricontract.contract.infrastructure.persistence.repository;

import com.agricontract.contract.infrastructure.persistence.entity.ContractNegotiationJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContractNegotiationJpaRepository extends JpaRepository<ContractNegotiationJpaEntity, Long> {
    List<ContractNegotiationJpaEntity> findByContractIdOrderByTermsRevisionAsc(String contractId);
}
