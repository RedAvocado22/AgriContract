package com.agricontract.contract.domain.event;

import lombok.Getter;

@Getter
public class ContractSettledEvent extends DomainEvent {
    private String buyerId;
    private String sellerId;

    public ContractSettledEvent(String contractId, String buyerEmail, String sellerEmail,
                                String buyerId, String sellerId) {
        super(contractId, buyerEmail, sellerEmail);
        this.buyerId = buyerId;
        this.sellerId = sellerId;
    }

    @Override
    public String getEventType() {
        return "contract.settled";
    }
}
