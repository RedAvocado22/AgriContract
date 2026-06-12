package com.agricontract.contract.domain.event;

import com.agricontract.contract.domain.model.vo.CancelledBy;
import lombok.Getter;

import java.util.UUID;

@Getter
public class ContractCancelledEvent extends DomainEvent {
    private CancelledBy cancelledBy;
    private String reason;

    public ContractCancelledEvent(UUID contractId, CancelledBy cancelledBy, String reason) {
        super(contractId);
        this.cancelledBy = cancelledBy;
        this.reason = reason;
    }

    @Override
    public String getEventType() {
        return "contract.cancelled";
    }
}
