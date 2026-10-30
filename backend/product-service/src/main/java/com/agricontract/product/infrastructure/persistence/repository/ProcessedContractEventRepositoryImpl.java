package com.agricontract.product.infrastructure.persistence.repository;

import com.agricontract.product.domain.model.vo.ContractId;
import com.agricontract.product.domain.model.vo.ContractLifecycleEventType;
import com.agricontract.product.domain.repository.ProcessedContractEventRepository;
import com.agricontract.product.infrastructure.persistence.entity.ProcessedContractEventJpaEntity;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ProcessedContractEventRepositoryImpl implements ProcessedContractEventRepository {
    private final ProcessedContractEventJpaRepository processedContractEventJpaRepository;
    private final EntityManager entityManager;
    private final Clock clock;

    @Override
    public boolean exists(UUID eventId) {
        return processedContractEventJpaRepository.existsById(eventId.toString());
    }

    @Override
    public void record(UUID eventId, ContractLifecycleEventType eventType, ContractId contractId) {
        // persist + flush, not save(): the id is assigned, so save() would merge into an existing row
        // instead of failing on the duplicate eventId
        entityManager.persist(ProcessedContractEventJpaEntity.builder()
                .eventId(eventId.toString())
                .eventType(eventType)
                .contractId(contractId.value())
                .processedAt(LocalDateTime.now(clock))
                .build());
        entityManager.flush();
    }

    @Override
    public boolean hasRecorded(ContractId contractId, ContractLifecycleEventType eventType) {
        return !processedContractEventJpaRepository.findByContractIdAndEventType(contractId.value(), eventType)
                .isEmpty();
    }
}
