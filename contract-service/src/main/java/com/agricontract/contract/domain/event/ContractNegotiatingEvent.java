package com.agricontract.contract.domain.event;

import com.agricontract.contract.domain.model.vo.ContractTerms;

import java.util.UUID;

public class ContractNegotiatingEvent extends DomainEvent {
    private String proposedBy;
    private ContractTerms proposedTerms;

    public ContractNegotiatingEvent(UUID contractId, String proposedBy, ContractTerms proposedTerms) {
        super(contractId);
        this.proposedBy = proposedBy;
        this.proposedTerms = proposedTerms;
    }
}
