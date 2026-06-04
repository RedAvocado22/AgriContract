package com.agricontract.contract.domain.event;

import com.agricontract.contract.domain.model.vo.ContractStatus;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ContractCancelledEvent implements DomainEvent {
    private String eventId;
    private String eventType;
    private String aggregateId;
    private String contractId;
    private String buyerId;
    private String sellerId;
    private ContractStatus cancellationType; // CANCELLED_BY_BUYER or CANCELLED_BY_SELLER
    private String reason;
    private LocalDateTime occurredAt;
}
