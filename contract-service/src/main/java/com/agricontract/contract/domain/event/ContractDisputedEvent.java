package com.agricontract.contract.domain.event;

import lombok.Getter;

import java.util.UUID;

@Getter
public class ContractDisputedEvent extends DomainEvent {
    private String disputedBy;
    private String reason;

    public ContractDisputedEvent(UUID contractId, String disputedBy, String reason) {
        super(contractId);
        this.disputedBy = disputedBy;
        this.reason = reason;
    }

    @Override
    public String getEventType() {
        return "contract.disputed";
    }
}
