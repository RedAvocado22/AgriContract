package com.agricontract.product.infrastructure.persistence.repository;

import com.agricontract.product.domain.model.vo.ContractLifecycleEventType;
import com.agricontract.product.infrastructure.persistence.entity.ProcessedContractEventJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;

public interface ProcessedContractEventJpaRepository extends JpaRepository<ProcessedContractEventJpaEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_READ)
    List<ProcessedContractEventJpaEntity> findByContractIdAndEventType(String contractId,
                                                                       ContractLifecycleEventType eventType);
}
