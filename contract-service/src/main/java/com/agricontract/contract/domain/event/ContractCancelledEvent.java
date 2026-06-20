package com.agricontract.contract.domain.event;

import com.agricontract.contract.domain.model.vo.CancelledBy;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class ContractCancelledEvent extends DomainEvent {
    private CancelledBy cancelledBy;
    private String reason;
    private BigDecimal buyerPenaltyRate;

    public ContractCancelledEvent(String contractId, String buyerEmail, String sellerEmail,
                                  CancelledBy cancelledBy, BigDecimal buyerPenaltyRate, String reason) {
        super(contractId, buyerEmail, sellerEmail);
        this.cancelledBy = cancelledBy;
        this.reason = reason;
        this.buyerPenaltyRate = buyerPenaltyRate;
    }

    @Override
    public String getEventType() {
        return "contract.cancelled";
    }
}
