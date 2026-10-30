package com.agricontract.product.domain.repository;

import com.agricontract.product.domain.model.vo.ContractId;
import com.agricontract.product.domain.model.vo.ContractLifecycleEventType;

import java.util.UUID;

/** Durable dedup store for consumed contract lifecycle events, keyed by envelope eventId. */
public interface ProcessedContractEventRepository {

    boolean exists(UUID eventId);

    /**
     * Insert only — a concurrent duplicate delivery violates the primary key, rolls back the caller's
     * transaction and is skipped on redelivery.
     */
    void record(UUID eventId, ContractLifecycleEventType eventType, ContractId contractId);

    /** Latest committed state (locking read), so a caller holding the reservation lock sees a just-committed event. */
    boolean hasRecorded(ContractId contractId, ContractLifecycleEventType eventType);
}
