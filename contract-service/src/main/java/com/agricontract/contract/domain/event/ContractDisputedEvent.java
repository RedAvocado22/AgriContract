package com.agricontract.contract.domain.event;

import lombok.Getter;

@Getter
public class ContractDisputedEvent extends DomainEvent {
    private String disputedBy;
    private String reason;

    public ContractDisputedEvent(String contractId, String buyerEmail, String sellerEmail,
                                 String disputedBy, String reason) {
        super(contractId, buyerEmail, sellerEmail);
        this.disputedBy = disputedBy;
        this.reason = reason;
    }

    @Override
    public String getEventType() {
        return "contract.disputed";
    }
}
