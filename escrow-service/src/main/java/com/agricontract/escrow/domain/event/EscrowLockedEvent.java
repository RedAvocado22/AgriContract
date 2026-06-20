package com.agricontract.escrow.domain.event;

import lombok.Getter;

@Getter
public class EscrowLockedEvent extends DomainEvent {

    public EscrowLockedEvent(String escrowId, String contractId, String buyerEmail, String sellerEmail) {
        super(escrowId, contractId, buyerEmail, sellerEmail);
    }

    @Override
    public String getEventType() {
        return "escrow.locked";
    }
}
