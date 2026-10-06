package com.agricontract.contract.domain.event;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public abstract class DomainEvent {
    protected UUID eventId;

    protected String contractId;

    protected Instant occurredAt;

    protected String buyerEmail;
    protected String sellerEmail;

    protected DomainEvent(String contractId, String buyerEmail, String sellerEmail) {
        this.eventId = UUID.randomUUID();
        this.buyerEmail = buyerEmail;
        this.sellerEmail = sellerEmail;
        this.contractId = contractId;
        this.occurredAt = Instant.now();
    }

    public abstract String getEventType();
}
