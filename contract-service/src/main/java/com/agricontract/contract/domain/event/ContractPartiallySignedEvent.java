package com.agricontract.contract.domain.event;

import lombok.Getter;

import java.util.Set;

@Getter
public class ContractPartiallySignedEvent extends DomainEvent {
    private String signedBy;
    private Set<String> remainingSignatories;

    public ContractPartiallySignedEvent(String contractId, String buyerEmail, String sellerEmail,
                                        String signedBy, Set<String> remainingSignatories) {
        super(contractId, buyerEmail, sellerEmail);
        this.signedBy = signedBy;
        this.remainingSignatories = remainingSignatories;
    }

    @Override
    public String getEventType() {
        return "contract.partiallySigned";
    }
}
