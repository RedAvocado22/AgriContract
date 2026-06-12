package com.agricontract.contract.domain.event;

import com.agricontract.contract.domain.model.vo.ContractTerms;
import lombok.Getter;

@Getter
public class ContractNegotiatingEvent extends DomainEvent {
    private String proposedBy;
    private ContractTerms proposedTerms;

    public ContractNegotiatingEvent(String contractId, String proposedBy, ContractTerms proposedTerms) {
        super(contractId);
        this.proposedBy = proposedBy;
        this.proposedTerms = proposedTerms;
    }

    @Override
    public String getEventType() {
        return "contract.negotiating";
    }
}
