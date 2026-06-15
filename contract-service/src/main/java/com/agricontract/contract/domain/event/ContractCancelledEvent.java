package com.agricontract.contract.domain.event;

import com.agricontract.contract.domain.model.vo.CancelledBy;
import lombok.Getter;

@Getter
public class ContractCancelledEvent extends DomainEvent {
    private CancelledBy cancelledBy;
    private String reason;

    public ContractCancelledEvent(String contractId, String buyerEmail, String sellerEmail,
                                  CancelledBy cancelledBy, String reason) {
        super(contractId, buyerEmail, sellerEmail);
        this.cancelledBy = cancelledBy;
        this.reason = reason;
    }

    @Override
    public String getEventType() {
        return "contract.cancelled";
    }
}
