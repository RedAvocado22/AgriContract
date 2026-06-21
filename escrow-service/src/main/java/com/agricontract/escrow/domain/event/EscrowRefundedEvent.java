package com.agricontract.escrow.domain.event;

import com.agricontract.escrow.domain.model.vo.Money;
import com.agricontract.escrow.domain.model.vo.Party;
import lombok.Getter;

@Getter
public class EscrowRefundedEvent extends DomainEvent {
    private final Party recipient;
    private final Money refundAmount;

    public EscrowRefundedEvent(String escrowId, String contractId, String buyerEmail, String sellerEmail,
                               Party recipient, Money refundAmount) {
        super(escrowId, contractId, buyerEmail, sellerEmail);
        this.recipient = recipient;
        this.refundAmount = refundAmount;
    }

    @Override
    public String getEventType() {
        return "escrow.refunded";
    }
}
