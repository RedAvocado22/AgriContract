package com.agricontract.contract.domain.event;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
public class ContractArbitratedEvent implements DomainEvent {
    private String eventId;
    private String eventType;
    private String aggregateId;
    private String contractId;
    private String buyerId;
    private String sellerId;
    private BigDecimal penaltyAmount;
    private String currency;
    private boolean penalizeBuyer;
    private LocalDateTime occurredAt;
}
