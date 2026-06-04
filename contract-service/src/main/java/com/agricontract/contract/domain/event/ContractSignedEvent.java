package com.agricontract.contract.domain.event;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
public class ContractSignedEvent implements DomainEvent {
    private String eventId;
    private String eventType;
    private String aggregateId;
    private String contractId;
    private String buyerId;
    private String sellerId;
    private BigDecimal agreedPrice;
    private String currency;
    private LocalDate deliveryDeadline;
    private LocalDateTime occurredAt;
}
