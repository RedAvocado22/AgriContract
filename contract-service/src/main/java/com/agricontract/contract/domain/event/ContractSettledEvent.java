package com.agricontract.contract.domain.event;

import java.util.UUID;

public class ContractSettledEvent extends DomainEvent {
    private String buyerId;
    private String sellerId;

    public ContractSettledEvent(UUID contractId, String buyerId, String sellerId) {
        super(contractId);
        this.buyerId = buyerId;
        this.sellerId = sellerId;
    }
}
