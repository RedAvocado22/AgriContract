package com.agricontract.product.domain.event;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public abstract class DomainEvent {
    protected UUID eventId;
    protected Instant occurredAt;

    protected DomainEvent() {
        this.eventId = UUID.randomUUID();
        this.occurredAt = Instant.now();
    }

    public abstract String getEventType();
}
