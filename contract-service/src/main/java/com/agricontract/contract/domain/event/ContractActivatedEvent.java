package com.agricontract.contract.domain.event;

import lombok.Getter;

@Getter
public class ContractActivatedEvent extends DomainEvent {
    public ContractActivatedEvent(String contractId, String buyerEmail, String sellerEmail) {
        super(contractId, buyerEmail, sellerEmail);
    }

    @Override
    public String getEventType() {
        return "contract.activated";
    }
}
