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

        EscrowTransaction lockBuyerPayment = EscrowTransaction.create(
                account.escrowId.value(),
                TransactionType.LOCK,
                amount,
                "Lock buyer payment."
        );
        account.transactions.add(lockBuyerPayment);
        return account;
    }

    public void lockSellerDeposit(BigDecimal sellerDepositRate) {
        //Guard
        if (this.status != EscrowStatus.BUYER_LOCKED) {
            throw new IllegalStateException("Buyer payment not locked yet.");
        }

        //Mutate
        this.sellerDeposit = totalAmount.multiply(sellerDepositRate);
        this.status = EscrowStatus.FULLY_LOCKED;

        //Emit
        EscrowTransaction lockSellerDeposit = EscrowTransaction.create(
                this.escrowId.value(),
                TransactionType.LOCK,
                this.sellerDeposit,
                "Lock seller deposit."
        );
        this.transactions.add(lockSellerDeposit);
    }

    /**
     * Called on contract.settled event → escrow released to seller
     */
    public void release() {
        //Guard
        if (this.status != EscrowStatus.FULLY_LOCKED) {
            throw new IllegalStateException("This payment is not fully locked yet.");
        }

        //Mutate
        this.status = EscrowStatus.RELEASED;

        //Emit
        EscrowTransaction releaseToSeller = EscrowTransaction.create(
                this.escrowId.value(),
                TransactionType.RELEASE,
                this.totalAmount,
                "Release buyer payment to seller."
        );
        this.transactions.add(releaseToSeller);

        EscrowTransaction refundToSeller = EscrowTransaction.create(
                this.escrowId.value(),
                TransactionType.REFUND_TO_SELLER,
                this.sellerDeposit,
                "Refund seller deposit."
        );
        this.transactions.add(refundToSeller);
    }

    /**
     * Called on contract.arbitrated event with penalizeBuyer=true
     */
    public void penalizeBuyer(BigDecimal buyerPenaltyRate) {
        //Guard
        if (this.status != EscrowStatus.FULLY_LOCKED) {
            throw new IllegalStateException("This payment not fully locked yet.");
        }
        //Mutate
        this.status = EscrowStatus.PENALIZED_BUYER;
        //Emit
        Money penalty = totalAmount.multiply(buyerPenaltyRate);

        EscrowTransaction penalizedBuyer = EscrowTransaction.create(
                this.escrowId.value(),
                TransactionType.PENALIZE_BUYER,
                penalty,
                "Penalize buyer payment to seller."
        );
        this.transactions.add(penalizedBuyer);

        EscrowTransaction refundToBuyer = EscrowTransaction.create(
                this.escrowId.value(),
                TransactionType.REFUND_TO_BUYER,
                this.totalAmount.subtract(penalty),
                "Refund buyer payment."
        );
        this.transactions.add(refundToBuyer);

        EscrowTransaction refundToSeller = EscrowTransaction.create(
                this.escrowId.value(),
                TransactionType.REFUND_TO_SELLER,
                this.sellerDeposit,
                "Refund seller deposit."
        );
        this.transactions.add(refundToSeller);
    }

    /**
     * Called on contract.arbitrated event with penalizeBuyer=false
     */
    public void penalizeSeller() {
        //Guard
        if (this.status != EscrowStatus.FULLY_LOCKED) {
            throw new IllegalStateException("This payment not fully locked yet.");
        }
        this.status = EscrowStatus.PENALIZED_SELLER;
        //Emit
        EscrowTransaction penalizedSeller = EscrowTransaction.create(
                this.escrowId.value(),
                TransactionType.PENALIZE_SELLER,
                this.sellerDeposit,
                "Penalize seller deposit to buyer."
        );
        this.transactions.add(penalizedSeller);

        EscrowTransaction refundToBuyer = EscrowTransaction.create(
                this.escrowId.value(),
                TransactionType.REFUND_TO_BUYER,
                this.totalAmount,
                "Refund buyer payment."
        );
        this.transactions.add(refundToBuyer);
    }

    public void arbitrate(Money buyerAmount, Money sellerAmount, String justification) {
        //Guard
        if (this.status != EscrowStatus.FULLY_LOCKED) {
            throw new IllegalStateException("This payment not fully locked yet.");
        }

        if (!buyerAmount.add(sellerAmount).equals(this.totalAmount.add(this.sellerDeposit))) {
            throw new IllegalArgumentException("Arbitration split must equal totalAmount + sellerDeposit.");
        }

        if (justification == null || justification.isBlank()) {
            throw new IllegalArgumentException("Justification cannot be blank.");
        }

        //Mutate
        this.status = EscrowStatus.ARBITRATED;
        //Emit
        EscrowTransaction buyerArbitration = EscrowTransaction.create(
                this.escrowId.value(),
                TransactionType.ARBITRATION_BUYER,
                buyerAmount,
                justification
        );
        this.transactions.add(buyerArbitration);

        EscrowTransaction sellerArbitration = EscrowTransaction.create(
                this.escrowId.value(),
                TransactionType.ARBITRATION_SELLER,
                sellerAmount,
                justification
        );
        this.transactions.add(sellerArbitration);
    }
}
