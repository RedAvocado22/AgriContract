package com.agricontract.escrow.domain.model;

import com.agricontract.escrow.domain.model.vo.EscrowId;
import com.agricontract.escrow.domain.model.vo.EscrowStatus;
import com.agricontract.escrow.domain.model.vo.Money;
import lombok.Getter;

import java.util.List;

// Aggregate Root
// Idempotency: dùng contractId làm idempotency key
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

    /** Gọi khi nhận event contract.signed */
    public static EscrowAccount lock(String contractId, String buyerId,
                                      String sellerId, Money amount) {
        // TODO
        throw new UnsupportedOperationException("TODO");
    }

    /** Gọi khi nhận event contract.settled → escrow released to seller */
    public void release() { /* TODO */ }

    /** Gọi khi nhận event contract.arbitrated với penalizeBuyer=true */
    public void penalizeBuyer(Money penalty) { /* TODO */ }

    /** Gọi khi nhận event contract.arbitrated với penalizeBuyer=false */
    public void penalizeSeller(Money penalty) { /* TODO */ }
}
