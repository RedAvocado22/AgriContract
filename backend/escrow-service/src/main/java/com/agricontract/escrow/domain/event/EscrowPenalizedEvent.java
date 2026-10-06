package com.agricontract.escrow.domain.event;

import com.agricontract.escrow.domain.model.vo.Money;
import com.agricontract.escrow.domain.model.vo.Party;
import lombok.Getter;

@Getter
public class EscrowPenalizedEvent extends DomainEvent {
    private final Party penalizedParty;
    private final Money penaltyAmount;

    public EscrowPenalizedEvent(String escrowId, String contractId, String buyerEmail, String sellerEmail,
                                Party penalizedParty, Money penaltyAmount) {
        super(escrowId, contractId, buyerEmail, sellerEmail);
        this.penalizedParty = penalizedParty;
        this.penaltyAmount = penaltyAmount;
    }

    @Override
    public String getEventType() {
        return "escrow.penalized";
    }
}
