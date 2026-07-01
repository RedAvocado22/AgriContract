package com.agricontract.contract.infrastructure.persistence.repository;

import com.agricontract.contract.domain.model.vo.ContractStatus;
import com.agricontract.contract.infrastructure.persistence.entity.ContractJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContractJpaRepository extends JpaRepository<ContractJpaEntity, Long> {
    Optional<ContractJpaEntity> findByContractId(String contractId);
    List<ContractJpaEntity> findByBuyerId(String buyerId);
    List<ContractJpaEntity> findBySellerId(String sellerId);
    List<ContractJpaEntity> findByStatus(ContractStatus status);
    Page<ContractJpaEntity> findByBuyerId(String buyerId, Pageable pageable);
    Page<ContractJpaEntity> findByBuyerIdAndStatus(String buyerId, ContractStatus status, Pageable pageable);
    Page<ContractJpaEntity> findBySellerId(String sellerId, Pageable pageable);
    Page<ContractJpaEntity> findBySellerIdAndStatus(String sellerId, ContractStatus status, Pageable pageable);
    long countByBuyerId(String buyerId);
    long countByBuyerIdAndStatus(String buyerId, ContractStatus status);
    long countBySellerId(String sellerId);
    long countBySellerIdAndStatus(String sellerId, ContractStatus status);
}
