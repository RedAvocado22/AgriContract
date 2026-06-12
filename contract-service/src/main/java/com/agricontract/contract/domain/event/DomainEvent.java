package com.agricontract.contract.domain.event;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public abstract class DomainEvent {
    protected UUID eventId;

    protected UUID contractId;

    protected Instant occurredAt;

    protected DomainEvent(UUID contractId) {
        this.eventId = UUID.randomUUID();
        this.contractId = contractId;
        this.occurredAt = Instant.now();
    }

    public abstract String getEventType();
}
