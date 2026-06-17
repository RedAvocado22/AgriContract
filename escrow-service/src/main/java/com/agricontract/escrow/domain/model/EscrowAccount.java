package com.agricontract.escrow.domain.model;

import com.agricontract.escrow.domain.model.vo.EscrowId;
import com.agricontract.escrow.domain.model.vo.EscrowStatus;
import com.agricontract.escrow.domain.model.vo.Money;
import com.agricontract.escrow.domain.model.vo.TransactionType;
import lombok.AccessLevel;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
    private Money sellerDeposit;
    private EscrowStatus status;
    @Getter(AccessLevel.NONE)
    private List<EscrowTransaction> transactions; // append-only ledger

    private EscrowAccount() {
        this.escrowId = new EscrowId(UUID.randomUUID().toString());
        this.transactions = new ArrayList<>();
    }

    /**
     * Called on contract.signed event
     */
    public static EscrowAccount lockBuyerPayment(String contractId, String buyerId,
                                                 String sellerId, Money amount) {
        EscrowAccount account = new EscrowAccount();
        account.contractId = contractId;
        account.buyerUserId = buyerId;
        account.sellerUserId = sellerId;
        account.totalAmount = amount;
        account.status = EscrowStatus.BUYER_LOCKED;

        EscrowTransaction transaction = EscrowTransaction.create(account.escrowId.value(), TransactionType.LOCK, amount, "Lock buyer payment.");
        account.transactions.add(transaction);
        return account;
    }

    public void lockSellerDeposit(BigDecimal sellerDepositRate) {
        if (this.status != EscrowStatus.BUYER_LOCKED) {
            throw new IllegalStateException("Buyer payment not locked yet.");
        }

        this.sellerDeposit = totalAmount.multiply(sellerDepositRate);
        this.status = EscrowStatus.FULLY_LOCKED;

        EscrowTransaction transaction = EscrowTransaction.create(this.escrowId.value(), TransactionType.LOCK, this.sellerDeposit, "Lock seller deposit.");
        this.transactions.add(transaction);
    }

    /**
     * Called on contract.settled event → escrow released to seller
     */
    public void release() {
        //Guard
        //Mutate
        //Emit
    }

    /**
     * Called on contract.arbitrated event with penalizeBuyer=true
     */
    public void penalizeBuyer(Money penalty) {
        //Guard
        //Mutate
        //Emit
    }

    /**
     * Called on contract.arbitrated event with penalizeBuyer=false
     */
    public void penalizeSeller(Money penalty) {
        //Guard
        //Mutate
        //Emit
    }
}
