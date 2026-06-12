package com.agricontract.contract.domain.event;

import lombok.Getter;

import java.util.UUID;

@Getter
public class ContractSettledEvent extends DomainEvent {
    private String buyerId;
    private String sellerId;

    public ContractSettledEvent(UUID contractId, String buyerId, String sellerId) {
        super(contractId);
        this.buyerId = buyerId;
        this.sellerId = sellerId;
    }

    @Override
    public String getEventType() {
        return "contract.settled";
    }
}
