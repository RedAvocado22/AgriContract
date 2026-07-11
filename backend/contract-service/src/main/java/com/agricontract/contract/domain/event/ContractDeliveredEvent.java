package com.agricontract.contract.domain.event;

import lombok.Getter;

@Getter
public class ContractDeliveredEvent extends DomainEvent {
    private String confirmedBy;

    public ContractDeliveredEvent(String contractId, String buyerEmail, String sellerEmail,
                                  String confirmedBy) {
        super(contractId, buyerEmail, sellerEmail);
        this.confirmedBy = confirmedBy;
    }

    @Override
    public String getEventType() {
        return "contract.delivered";
    }
}
