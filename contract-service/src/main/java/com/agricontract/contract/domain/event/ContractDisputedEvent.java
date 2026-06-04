package com.agricontract.contract.domain.event;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ContractDisputedEvent implements DomainEvent {
    private String eventId;
    private String eventType;
    private String aggregateId;
    private String contractId;
    private String buyerId;
    private String sellerId;
    private String reason;
    private LocalDateTime occurredAt;
}
