package com.agricontract.escrow.domain.event;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public abstract class DomainEvent {
    protected UUID eventId;
    protected Instant occurredAt;

    protected String escrowId;
    protected String contractId;

    protected String buyerEmail;
    protected String sellerEmail;

    protected DomainEvent(String escrowId, String contractId, String buyerEmail, String sellerEmail) {
        this.eventId = UUID.randomUUID();
        this.escrowId = escrowId;
        this.contractId = contractId;
        this.buyerEmail = buyerEmail;
        this.sellerEmail = sellerEmail;
        this.occurredAt = Instant.now();
    }

    public abstract String getEventType();
}
