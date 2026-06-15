package com.agricontract.contract.infrastructure.persistence.repository;

import com.agricontract.contract.domain.model.vo.ContractStatus;
import com.agricontract.contract.infrastructure.persistence.entity.ContractJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContractJpaRepository extends JpaRepository<ContractJpaEntity, Long> {
    Optional<ContractJpaEntity> findByContractId(String contractId);
    List<ContractJpaEntity> findByBuyerId(String buyerId);
    List<ContractJpaEntity> findBySellerId(String sellerId);
    List<ContractJpaEntity> findByStatus(ContractStatus status);
}
