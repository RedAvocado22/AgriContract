package com.agricontract.escrow.domain.event;

import com.agricontract.escrow.domain.model.vo.Money;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class EscrowLockBuyerPaymentEvent extends DomainEvent {
    private final Money sellerDepositAmount;
    private final BigDecimal sellerDepositRate;

    public EscrowLockBuyerPaymentEvent(String escrowId, String contractId, String buyerEmail, String sellerEmail, Money sellerDepositAmount, BigDecimal sellerDepositRate) {
        super(escrowId, contractId, buyerEmail, sellerEmail);
        this.sellerDepositAmount = sellerDepositAmount;
        this.sellerDepositRate = sellerDepositRate;
    }

    @Override
    public String getEventType() {
        return "escrow.buyer_locked";
    }
}
