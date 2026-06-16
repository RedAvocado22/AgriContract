package com.agricontract.contract.domain.event;

import com.agricontract.contract.domain.model.vo.ContractTerms;
import lombok.Getter;

@Getter
public class ContractOfferedEvent extends DomainEvent {
    private String buyerId;
    private String sellerId;
    private String listingId;
    private ContractTerms terms;

    public ContractOfferedEvent(String contractId, String buyerEmail, String sellerEmail,
                                String buyerId, String sellerId, String listingId, ContractTerms terms) {
        super(contractId, buyerEmail, sellerEmail);
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.listingId = listingId;
        this.terms = terms;
    }

    @Override
    public String getEventType() {
        return "contract.offered";
    }
}
