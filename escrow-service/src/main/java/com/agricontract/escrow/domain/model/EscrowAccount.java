package com.agricontract.escrow.domain.model;

import com.agricontract.escrow.domain.event.DomainEvent;
import com.agricontract.escrow.domain.event.EscrowLockedEvent;
import com.agricontract.escrow.domain.event.EscrowPenalizedEvent;
import com.agricontract.escrow.domain.event.EscrowReleasedEvent;
import com.agricontract.escrow.domain.event.EscrowRefundedEvent;
import com.agricontract.escrow.domain.model.vo.EscrowId;
import com.agricontract.escrow.domain.model.vo.EscrowStatus;
import com.agricontract.escrow.domain.model.vo.Money;
import com.agricontract.escrow.domain.model.vo.Party;
import com.agricontract.escrow.domain.model.vo.TransactionType;
import lombok.AccessLevel;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
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
    private String buyerEmail;
    private String sellerEmail;
    private Money totalAmount;
    private Money sellerDeposit;
    private BigDecimal sellerDepositRate;
    private EscrowStatus status;
    @Getter(AccessLevel.NONE)
    private List<EscrowTransaction> transactions; // append-only ledger
    @Getter(AccessLevel.NONE)
    private List<DomainEvent> domainEvents;

    private EscrowAccount() {
        this.escrowId = new EscrowId(UUID.randomUUID().toString());
        this.transactions = new ArrayList<>();
        this.domainEvents = new ArrayList<>();
    }

    public static EscrowAccount reconstitute(EscrowId escrowId, String contractId, String buyerUserId,
                                             String buyerEmail, String sellerEmail,
                                             String sellerUserId, Money totalAmount, Money sellerDeposit, EscrowStatus status,
                                             BigDecimal sellerDepositRate,
                                             List<EscrowTransaction> transactions) {
        EscrowAccount account = new EscrowAccount();

        account.escrowId = escrowId;
        account.contractId = contractId;
        account.buyerUserId = buyerUserId;
        account.buyerEmail = buyerEmail;
        account.sellerEmail = sellerEmail;
        account.sellerUserId = sellerUserId;
        account.totalAmount = totalAmount;
        account.sellerDeposit = sellerDeposit;
        account.status = status;
        account.sellerDepositRate = sellerDepositRate;
        account.transactions = new ArrayList<>(transactions);

        return account;
    }

    /**
     * Called on contract.signed event
     */
    public static EscrowAccount lockBuyerPayment(String contractId,
                                                 String buyerId, String sellerId,
                                                 String buyerEmail, String sellerEmail,
                                                 BigDecimal sellerDepositRate,
                                                 Money amount) {
        EscrowAccount account = new EscrowAccount();
        account.contractId = contractId;
        account.buyerUserId = buyerId;
        account.sellerUserId = sellerId;
        account.buyerEmail = buyerEmail;
        account.sellerEmail = sellerEmail;
        account.totalAmount = amount;
        account.sellerDepositRate = sellerDepositRate;
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

    public void lockSellerDeposit() {
        //Guard
        if (this.status != EscrowStatus.BUYER_LOCKED) {
            throw new IllegalStateException("Buyer payment not locked yet.");
        }

        //Mutate
        this.sellerDeposit = totalAmount.multiply(this.sellerDepositRate);
        this.status = EscrowStatus.FULLY_LOCKED;

        //Emit
        EscrowTransaction lockSellerDeposit = EscrowTransaction.create(
                this.escrowId.value(),
                TransactionType.LOCK,
                this.sellerDeposit,
                "Lock seller deposit."
        );
        this.transactions.add(lockSellerDeposit);
        this.domainEvents.add(new EscrowLockedEvent(this.escrowId.value(), this.contractId, this.buyerEmail, this.sellerEmail));
    }

    /**
     * Called on contract.delivered event → escrow released to seller
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
        this.domainEvents.add(new EscrowReleasedEvent(this.escrowId.value(), this.contractId, this.buyerEmail, this.sellerEmail));
        this.domainEvents.add(new EscrowRefundedEvent(this.escrowId.value(), this.contractId, this.buyerEmail, this.sellerEmail,
                Party.SELLER, this.sellerDeposit));
    }

    /**
     * Called on contract.cancelled event with cancelledBy=BUYER
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

        this.domainEvents.add(new EscrowPenalizedEvent(this.escrowId.value(), this.contractId, this.buyerEmail, this.sellerEmail,
                Party.BUYER, penalty));
        this.domainEvents.add(new EscrowRefundedEvent(this.escrowId.value(), this.contractId, this.buyerEmail, this.sellerEmail,
                Party.BUYER, this.totalAmount.subtract(penalty)));
        this.domainEvents.add(new EscrowRefundedEvent(this.escrowId.value(), this.contractId, this.buyerEmail, this.sellerEmail,
                Party.SELLER, this.sellerDeposit));
    }

    /**
     * Called on contract.cancelled event with cancelledBy=SELLER
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

        this.domainEvents.add(new EscrowPenalizedEvent(this.escrowId.value(), this.contractId, this.buyerEmail, this.sellerEmail,
                Party.SELLER, this.sellerDeposit));
        this.domainEvents.add(new EscrowRefundedEvent(this.escrowId.value(), this.contractId, this.buyerEmail, this.sellerEmail,
                Party.BUYER, this.totalAmount));
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

    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = new ArrayList<>(this.domainEvents);
        this.domainEvents.clear();
        return events;
    }

    public List<EscrowTransaction> getTransactions() {
        return Collections.unmodifiableList(this.transactions);
    }
}
