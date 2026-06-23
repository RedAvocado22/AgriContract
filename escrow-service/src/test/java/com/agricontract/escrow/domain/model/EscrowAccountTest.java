package com.agricontract.escrow.domain.model;

import com.agricontract.escrow.domain.event.DomainEvent;
import com.agricontract.escrow.domain.event.EscrowLockBuyerPaymentEvent;
import com.agricontract.escrow.domain.event.EscrowLockedEvent;
import com.agricontract.escrow.domain.event.EscrowPenalizedEvent;
import com.agricontract.escrow.domain.event.EscrowReleasedEvent;
import com.agricontract.escrow.domain.event.EscrowRefundedEvent;
import com.agricontract.escrow.domain.model.vo.EscrowStatus;
import com.agricontract.escrow.domain.model.vo.Money;
import com.agricontract.escrow.domain.model.vo.Party;
import com.agricontract.escrow.domain.model.vo.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class EscrowAccountTest {

    private static final String CONTRACT_ID = "contract-1";
    private static final String BUYER_ID = "buyer-1";
    private static final String SELLER_ID = "seller-1";
    private static final String BUYER_EMAIL = "buyer@agricontract.test";
    private static final String SELLER_EMAIL = "seller@agricontract.test";
    private static final Money TOTAL_AMOUNT = new Money(new BigDecimal("10000000"), "VND");
    private static final BigDecimal SELLER_DEPOSIT_RATE = new BigDecimal("0.1");
    private static final Money SELLER_DEPOSIT = new Money(new BigDecimal("1000000"), "VND");

    private EscrowAccount buyerLocked() {
        return EscrowAccount.lockBuyerPayment(CONTRACT_ID, BUYER_ID, SELLER_ID, BUYER_EMAIL, SELLER_EMAIL, SELLER_DEPOSIT_RATE, TOTAL_AMOUNT);
    }

    private EscrowAccount fullyLocked() {
        EscrowAccount account = buyerLocked();
        account.lockSellerDeposit();
        return account;
    }

    // ─── lockBuyerPayment ────────────────────────────────────────────────

    @Test
    void lockBuyerPayment_happyPath_createsAccountWithBuyerLockedStatusAndOneLockEntry() {
        EscrowAccount account = buyerLocked();

        assertThat(account.getStatus()).isEqualTo(EscrowStatus.BUYER_LOCKED);
        assertThat(account.getContractId()).isEqualTo(CONTRACT_ID);
        assertThat(account.getBuyerUserId()).isEqualTo(BUYER_ID);
        assertThat(account.getSellerUserId()).isEqualTo(SELLER_ID);
        assertThat(account.getTotalAmount()).isEqualTo(TOTAL_AMOUNT);
        assertThat(account.getEscrowId()).isNotNull();

        List<EscrowTransaction> transactions = account.getTransactions();
        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getType()).isEqualTo(TransactionType.LOCK);
        assertThat(transactions.get(0).getAmount()).isEqualTo(TOTAL_AMOUNT);
    }

    // ─── lockSellerDeposit ───────────────────────────────────────────────

    @Test
    void lockSellerDeposit_happyPath_transitionsToFullyLockedAndComputesDeposit() {
        EscrowAccount account = buyerLocked();

        account.lockSellerDeposit();

        assertThat(account.getStatus()).isEqualTo(EscrowStatus.FULLY_LOCKED);
        assertThat(account.getSellerDeposit()).isEqualTo(SELLER_DEPOSIT);

        List<EscrowTransaction> transactions = account.getTransactions();
        assertThat(transactions).hasSize(2);
        assertThat(transactions.get(1).getType()).isEqualTo(TransactionType.LOCK);
        assertThat(transactions.get(1).getAmount()).isEqualTo(SELLER_DEPOSIT);
    }

    @Test
    void lockSellerDeposit_whenNotBuyerLocked_throws() {
        EscrowAccount account = fullyLocked();

        assertThatThrownBy(account::lockSellerDeposit)
                .isInstanceOf(IllegalStateException.class);
    }

    // ─── release ─────────────────────────────────────────────────────────

    @Test
    void release_happyPath_transitionsToReleasedWithTwoEntries() {
        EscrowAccount account = fullyLocked();

        account.release();

        assertThat(account.getStatus()).isEqualTo(EscrowStatus.RELEASED);

        List<EscrowTransaction> transactions = account.getTransactions();
        assertThat(transactions).hasSize(4); // 2 lock + RELEASE + REFUND_TO_SELLER
        assertThat(transactions.get(2).getType()).isEqualTo(TransactionType.RELEASE);
        assertThat(transactions.get(2).getAmount()).isEqualTo(TOTAL_AMOUNT);
        assertThat(transactions.get(3).getType()).isEqualTo(TransactionType.REFUND_TO_SELLER);
        assertThat(transactions.get(3).getAmount()).isEqualTo(SELLER_DEPOSIT);
    }

    @Test
    void release_whenNotFullyLocked_throws() {
        EscrowAccount account = buyerLocked();

        assertThatThrownBy(account::release)
                .isInstanceOf(IllegalStateException.class);
    }

    // ─── penalizeBuyer ───────────────────────────────────────────────────

    @Test
    void penalizeBuyer_happyPath_transitionsToPenalizedBuyerWithThreeEntries() {
        EscrowAccount account = fullyLocked();
        BigDecimal penaltyRate = new BigDecimal("0.3");
        Money expectedPenalty = TOTAL_AMOUNT.multiply(penaltyRate);
        Money expectedRefundToBuyer = TOTAL_AMOUNT.subtract(expectedPenalty);

        account.penalizeBuyer(penaltyRate);

        assertThat(account.getStatus()).isEqualTo(EscrowStatus.PENALIZED_BUYER);

        List<EscrowTransaction> transactions = account.getTransactions();
        assertThat(transactions).hasSize(5); // 2 lock + PENALIZE_BUYER + REFUND_TO_BUYER + REFUND_TO_SELLER
        assertThat(transactions.get(2).getType()).isEqualTo(TransactionType.PENALIZE_BUYER);
        assertThat(transactions.get(2).getAmount()).isEqualTo(expectedPenalty);
        assertThat(transactions.get(3).getType()).isEqualTo(TransactionType.REFUND_TO_BUYER);
        assertThat(transactions.get(3).getAmount()).isEqualTo(expectedRefundToBuyer);
        assertThat(transactions.get(4).getType()).isEqualTo(TransactionType.REFUND_TO_SELLER);
        assertThat(transactions.get(4).getAmount()).isEqualTo(SELLER_DEPOSIT);
    }

    @Test
    void penalizeBuyer_remainderAllocation_penaltyPlusRefundAlwaysEqualsTotalAmount() {
        EscrowAccount account = fullyLocked();
        BigDecimal oddRate = new BigDecimal("0.33333"); // rate gây số lẻ

        account.penalizeBuyer(oddRate);

        List<EscrowTransaction> transactions = account.getTransactions();
        Money penalty = transactions.get(2).getAmount();
        Money refundToBuyer = transactions.get(3).getAmount();

        assertThat(penalty.add(refundToBuyer)).isEqualTo(TOTAL_AMOUNT);
    }

    @Test
    void penalizeBuyer_whenNotFullyLocked_throws() {
        EscrowAccount account = buyerLocked();

        assertThatThrownBy(() -> account.penalizeBuyer(new BigDecimal("0.3")))
                .isInstanceOf(IllegalStateException.class);
    }

    // ─── penalizeSeller ──────────────────────────────────────────────────

    @Test
    void penalizeSeller_happyPath_transitionsToPenalizedSellerWithTwoEntries() {
        EscrowAccount account = fullyLocked();

        account.penalizeSeller();

        assertThat(account.getStatus()).isEqualTo(EscrowStatus.PENALIZED_SELLER);

        List<EscrowTransaction> transactions = account.getTransactions();
        assertThat(transactions).hasSize(4); // 2 lock + PENALIZE_SELLER + REFUND_TO_BUYER
        assertThat(transactions.get(2).getType()).isEqualTo(TransactionType.PENALIZE_SELLER);
        assertThat(transactions.get(2).getAmount()).isEqualTo(SELLER_DEPOSIT);
        assertThat(transactions.get(3).getType()).isEqualTo(TransactionType.REFUND_TO_BUYER);
        assertThat(transactions.get(3).getAmount()).isEqualTo(TOTAL_AMOUNT);
    }

    @Test
    void penalizeSeller_whenNotFullyLocked_throws() {
        EscrowAccount account = buyerLocked();

        assertThatThrownBy(account::penalizeSeller)
                .isInstanceOf(IllegalStateException.class);
    }

    // ─── arbitrate ───────────────────────────────────────────────────────

    @Test
    void arbitrate_happyPath_transitionsToArbitratedWithTwoEntries() {
        EscrowAccount account = fullyLocked();
        Money buyerAmount = new Money(new BigDecimal("3000000"), "VND");
        Money sellerAmount = new Money(new BigDecimal("8000000"), "VND"); // 3M + 8M = 10M + 1M
        String justification = "Seller delivered 70% of agreed quality.";

        account.arbitrate(buyerAmount, sellerAmount, justification);

        assertThat(account.getStatus()).isEqualTo(EscrowStatus.ARBITRATED);

        List<EscrowTransaction> transactions = account.getTransactions();
        assertThat(transactions).hasSize(4); // 2 lock + ARBITRATION_BUYER + ARBITRATION_SELLER
        assertThat(transactions.get(2).getType()).isEqualTo(TransactionType.ARBITRATION_BUYER);
        assertThat(transactions.get(2).getAmount()).isEqualTo(buyerAmount);
        assertThat(transactions.get(2).getNote()).isEqualTo(justification);
        assertThat(transactions.get(3).getType()).isEqualTo(TransactionType.ARBITRATION_SELLER);
        assertThat(transactions.get(3).getAmount()).isEqualTo(sellerAmount);
        assertThat(transactions.get(3).getNote()).isEqualTo(justification);
    }

    @Test
    void arbitrate_whenNotFullyLocked_throwsIllegalState() {
        EscrowAccount account = buyerLocked();

        assertThatThrownBy(() -> account.arbitrate(
                new Money(new BigDecimal("3000000"), "VND"),
                new Money(new BigDecimal("8000000"), "VND"),
                "reason"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void arbitrate_whenSumDoesNotMatch_throwsIllegalArgument() {
        EscrowAccount account = fullyLocked();

        assertThatThrownBy(() -> account.arbitrate(
                new Money(new BigDecimal("3000000"), "VND"),
                new Money(new BigDecimal("7000000"), "VND"), // sum = 10M, missing the 1M deposit
                "reason"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void arbitrate_whenJustificationNull_throwsIllegalArgument() {
        EscrowAccount account = fullyLocked();

        assertThatThrownBy(() -> account.arbitrate(
                new Money(new BigDecimal("3000000"), "VND"),
                new Money(new BigDecimal("8000000"), "VND"),
                null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void arbitrate_whenJustificationBlank_throwsIllegalArgument() {
        EscrowAccount account = fullyLocked();

        assertThatThrownBy(() -> account.arbitrate(
                new Money(new BigDecimal("3000000"), "VND"),
                new Money(new BigDecimal("8000000"), "VND"),
                "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── domain events ───────────────────────────────────────────────────

    @Test
    void lockBuyerPayment_happyPath_emitsEscrowLockBuyerPaymentEvent() {
        EscrowAccount account = buyerLocked();

        List<DomainEvent> events = account.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(EscrowLockBuyerPaymentEvent.class);
        assertThat(events.get(0).getEventType()).isEqualTo("escrow.buyer_locked");

        EscrowLockBuyerPaymentEvent event = (EscrowLockBuyerPaymentEvent) events.get(0);
        assertThat(event.getSellerDepositAmount()).isEqualTo(SELLER_DEPOSIT);
        assertThat(event.getSellerDepositRate()).isEqualTo(SELLER_DEPOSIT_RATE);
    }

    @Test
    void lockSellerDeposit_happyPath_emitsEscrowLockedEvent() {
        EscrowAccount account = buyerLocked();
        account.pullDomainEvents(); // discard lockBuyerPayment's event

        account.lockSellerDeposit();

        List<DomainEvent> events = account.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(EscrowLockedEvent.class);
        assertThat(events.get(0).getEventType()).isEqualTo("escrow.locked");
    }

    @Test
    void release_happyPath_emitsReleasedAndRefundedToSellerEvents() {
        EscrowAccount account = fullyLocked();
        account.pullDomainEvents(); // discard lockSellerDeposit's event

        account.release();

        List<DomainEvent> events = account.pullDomainEvents();
        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(EscrowReleasedEvent.class);
        assertThat(events.get(1)).isInstanceOf(EscrowRefundedEvent.class);

        EscrowRefundedEvent refunded = (EscrowRefundedEvent) events.get(1);
        assertThat(refunded.getRecipient()).isEqualTo(Party.SELLER);
        assertThat(refunded.getRefundAmount()).isEqualTo(SELLER_DEPOSIT);
    }

    @Test
    void penalizeBuyer_happyPath_emitsPenalizedAndTwoRefundedEvents() {
        EscrowAccount account = fullyLocked();
        account.pullDomainEvents();
        BigDecimal penaltyRate = new BigDecimal("0.3");
        Money expectedPenalty = TOTAL_AMOUNT.multiply(penaltyRate);
        Money expectedRefundToBuyer = TOTAL_AMOUNT.subtract(expectedPenalty);

        account.penalizeBuyer(penaltyRate);

        List<DomainEvent> events = account.pullDomainEvents();
        assertThat(events).hasSize(3);

        EscrowPenalizedEvent penalized = (EscrowPenalizedEvent) events.get(0);
        assertThat(penalized.getPenalizedParty()).isEqualTo(Party.BUYER);
        assertThat(penalized.getPenaltyAmount()).isEqualTo(expectedPenalty);

        EscrowRefundedEvent refundToBuyer = (EscrowRefundedEvent) events.get(1);
        assertThat(refundToBuyer.getRecipient()).isEqualTo(Party.BUYER);
        assertThat(refundToBuyer.getRefundAmount()).isEqualTo(expectedRefundToBuyer);

        EscrowRefundedEvent refundToSeller = (EscrowRefundedEvent) events.get(2);
        assertThat(refundToSeller.getRecipient()).isEqualTo(Party.SELLER);
        assertThat(refundToSeller.getRefundAmount()).isEqualTo(SELLER_DEPOSIT);
    }

    @Test
    void penalizeSeller_happyPath_emitsPenalizedAndRefundedEvent() {
        EscrowAccount account = fullyLocked();
        account.pullDomainEvents();

        account.penalizeSeller();

        List<DomainEvent> events = account.pullDomainEvents();
        assertThat(events).hasSize(2);

        EscrowPenalizedEvent penalized = (EscrowPenalizedEvent) events.get(0);
        assertThat(penalized.getPenalizedParty()).isEqualTo(Party.SELLER);
        assertThat(penalized.getPenaltyAmount()).isEqualTo(SELLER_DEPOSIT);

        EscrowRefundedEvent refundToBuyer = (EscrowRefundedEvent) events.get(1);
        assertThat(refundToBuyer.getRecipient()).isEqualTo(Party.BUYER);
        assertThat(refundToBuyer.getRefundAmount()).isEqualTo(TOTAL_AMOUNT);
    }

    @Test
    void arbitrate_happyPath_emitsNoEvent() {
        EscrowAccount account = fullyLocked();
        account.pullDomainEvents();

        account.arbitrate(
                new Money(new BigDecimal("3000000"), "VND"),
                new Money(new BigDecimal("8000000"), "VND"),
                "reason");

        assertThat(account.pullDomainEvents()).isEmpty();
    }

    @Test
    void pullDomainEvents_clearsListAfterPull() {
        EscrowAccount account = buyerLocked();
        account.lockSellerDeposit();

        account.pullDomainEvents();

        assertThat(account.pullDomainEvents()).isEmpty();
    }

    // ─── getTransactions immutability ───────────────────────────────────

    @Test
    void getTransactions_returnsUnmodifiableList() {
        EscrowAccount account = buyerLocked();

        List<EscrowTransaction> transactions = account.getTransactions();

        assertThatThrownBy(() -> transactions.add(
                EscrowTransaction.create(account.getEscrowId().value(), TransactionType.LOCK, TOTAL_AMOUNT, "hack")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
