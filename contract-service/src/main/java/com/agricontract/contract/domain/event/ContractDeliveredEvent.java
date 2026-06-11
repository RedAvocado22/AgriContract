package com.agricontract.contract.domain.event;

import java.util.UUID;

public class ContractDeliveredEvent extends DomainEvent {
    private String confirmedBy;

    public ContractDeliveredEvent(UUID contractId, String confirmedBy) {
        super(contractId);
        this.confirmedBy = confirmedBy;
    }
}
