package com.agricontract.escrow.domain.event;

import com.agricontract.escrow.domain.model.vo.Money;
import lombok.Getter;

@Getter
public class EscrowArbitratedEvent extends DomainEvent {
    private final Money buyerAmount;
    private final Money sellerAmount;
    private final String justification;

    public EscrowArbitratedEvent(
            String escrowId, String contractId, String buyerEmail, String sellerEmail,
            Money buyerAmount, Money sellerAmount, String justification) {
        super(escrowId, contractId, buyerEmail, sellerEmail);
        this.buyerAmount = buyerAmount;
        this.sellerAmount = sellerAmount;
        this.justification = justification;
    }

    @Override
    public String getEventType() {
        return "escrow.arbitrated";
    }
}
