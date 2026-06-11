package com.agricontract.contract.domain.event;

import java.util.UUID;

public class ContractDisputedEvent extends DomainEvent {
    private String disputedBy;
    private String reason;

    public ContractDisputedEvent(UUID contractId, String disputedBy, String reason) {
        super(contractId);
        this.disputedBy = disputedBy;
        this.reason = reason;
    }
}
