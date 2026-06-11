package com.agricontract.contract.domain.event;

import com.agricontract.contract.domain.model.vo.CancelledBy;

import java.util.UUID;

public class ContractCancelledEvent extends DomainEvent {
    private CancelledBy cancelledBy;
    private String reason;

    public ContractCancelledEvent(UUID contractId, CancelledBy cancelledBy, String reason) {
        super(contractId);
        this.cancelledBy = cancelledBy;
        this.reason = reason;
    }
}
