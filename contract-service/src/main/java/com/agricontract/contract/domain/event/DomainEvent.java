package com.agricontract.contract.domain.event;

import java.time.Instant;
import java.util.UUID;

public abstract class DomainEvent {
    protected UUID eventId;

    protected UUID contractId;

    protected Instant occurredAt;

    protected DomainEvent(UUID contractId) {
        this.eventId = UUID.randomUUID();
        this.contractId = contractId;
        this.occurredAt = Instant.now();
    }
}
