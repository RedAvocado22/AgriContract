package com.agricontract.escrow.domain.model;

import com.agricontract.escrow.domain.model.vo.EscrowId;
import com.agricontract.escrow.domain.model.vo.EscrowStatus;
import com.agricontract.escrow.domain.model.vo.Money;
import lombok.Getter;

import java.util.List;

// Aggregate Root
// Idempotency: contractId is used as the idempotency key
// State machine: LOCKED → RELEASED | PENALIZED_BUYER | PENALIZED_SELLER | ARBITRATED
@Getter
public class EscrowAccount {

    private EscrowId escrowId;
    private String contractId;      // idempotency key
    private String buyerUserId;
    private String sellerUserId;
    private Money totalAmount;
    private EscrowStatus status;
    private List<EscrowTransaction> transactions; // append-only ledger

    private EscrowAccount() {}

    /** Called on contract.signed event */
    public static EscrowAccount lock(String contractId, String buyerId,
                                      String sellerId, Money amount) {
        // TODO
        throw new UnsupportedOperationException("TODO");
    }

    /** Called on contract.settled event → escrow released to seller */
    public void release() { /* TODO */ }

    /** Called on contract.arbitrated event with penalizeBuyer=true */
    public void penalizeBuyer(Money penalty) { /* TODO */ }

    /** Called on contract.arbitrated event with penalizeBuyer=false */
    public void penalizeSeller(Money penalty) { /* TODO */ }
}
