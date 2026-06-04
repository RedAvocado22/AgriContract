package com.agricontract.contract.domain.event;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
public class ContractSettledEvent implements DomainEvent {
    private String eventId;
    private String eventType;
    private String aggregateId;
    private String contractId;
    private String buyerId;
    private String sellerId;
    private BigDecimal amount;
    private String currency;
    private LocalDateTime occurredAt;
}
