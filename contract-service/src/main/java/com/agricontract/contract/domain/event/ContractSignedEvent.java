package com.agricontract.contract.domain.event;

import com.agricontract.contract.domain.model.vo.ContractTerms;

import java.util.UUID;

public class ContractSignedEvent extends DomainEvent {

    private String buyerId;
    private String sellerId;
    private String listingId;
    private ContractTerms terms;

    public ContractSignedEvent(
            UUID contractId,
            String buyerId, String sellerId, String listingId,
            ContractTerms terms) {
        super(contractId);
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.listingId = listingId;
        this.terms = terms;
    }
}
