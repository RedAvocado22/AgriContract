package com.agricontract.contract.domain.event;

import lombok.Getter;

import java.util.UUID;

@Getter
public class ContractDeliveredEvent extends DomainEvent {
    private String confirmedBy;

    public ContractDeliveredEvent(UUID contractId, String confirmedBy) {
        super(contractId);
        this.confirmedBy = confirmedBy;
    }

    @Override
    public String getEventType() {
        return "contract.delivered";
    }
}
