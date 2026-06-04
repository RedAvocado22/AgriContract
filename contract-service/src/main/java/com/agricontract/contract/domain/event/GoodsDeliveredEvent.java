package com.agricontract.contract.domain.event;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class GoodsDeliveredEvent implements DomainEvent {
    private String eventId;
    private String eventType;
    private String aggregateId;
    private String contractId;
    private String buyerId;
    private String sellerId;
    private LocalDateTime occurredAt;
}
