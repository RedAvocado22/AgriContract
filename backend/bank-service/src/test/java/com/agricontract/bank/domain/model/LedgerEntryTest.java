package com.agricontract.bank.domain.model;

import com.agricontract.bank.domain.exception.InvalidLedgerEntryException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.agricontract.bank.domain.model.EntryType.*;
import static com.agricontract.bank.domain.model.FundType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class LedgerEntryTest {

    private static final Instant NOW = Instant.parse("2026-10-27T03:00:00Z");
    private static final Money AMOUNT = new Money(new BigDecimal("100.00"));

    private static String id() {
        return UUID.randomUUID().toString();
    }

    private static LedgerEntry entry(EntryType type, FundType fund, String milestoneId, String decision, String leg) {
        return LedgerEntry.create(id(), id(), milestoneId, id(), type, fund, decision, leg, AMOUNT, NOW);
    }

    @Test
    void createGeneratesIdentityAndKeepsTheGivenTime() {
        LedgerEntry e = entry(LOCK_BUYER_DEPOSIT, BUYER_DEPOSIT, null, null, null);
        assertThat(UUID.fromString(e.getEntryId())).isNotNull();
        assertThat(e.getCreatedAt()).isEqualTo(NOW);
        assertThat(e.getAmount()).isEqualTo(AMOUNT);
    }

    @Test
    void rejectsAWrongFund() {
        assertThatThrownBy(() -> entry(RELEASE_TO_SELLER, BUYER_DEPOSIT, null, null, null))
                .isInstanceOf(InvalidLedgerEntryException.class).hasMessageContaining("cannot move");
    }

    @Test
    void milestoneIdOnlyWithMilestonePayment() {
        assertThatThrownBy(() -> entry(LOCK_MILESTONE, MILESTONE_PAYMENT, null, null, null))
                .isInstanceOf(InvalidLedgerEntryException.class).hasMessageContaining("needs a milestoneId");
        assertThatThrownBy(() -> entry(LOCK_SELLER_DEPOSIT, SELLER_DEPOSIT, id(), null, null))
                .isInstanceOf(InvalidLedgerEntryException.class).hasMessageContaining("contract-level");
        assertThatCode(() -> entry(REFUND_TO_BUYER, MILESTONE_PAYMENT, id(), null, null)).doesNotThrowAnyException();
    }

    @Test
    void remedyIdsComeTogether() {
        assertThatThrownBy(() -> entry(REFUND_TO_BUYER, BUYER_DEPOSIT, null, id(), null))
                .isInstanceOf(InvalidLedgerEntryException.class).hasMessageContaining("come together");
        assertThatThrownBy(() -> entry(REFUND_TO_BUYER, BUYER_DEPOSIT, null, null, id()))
                .isInstanceOf(InvalidLedgerEntryException.class).hasMessageContaining("come together");
    }

    @Test
    void locksAreNeverRemedyLegs() {
        assertThatThrownBy(() -> entry(LOCK_BUYER_DEPOSIT, BUYER_DEPOSIT, null, id(), id()))
                .isInstanceOf(InvalidLedgerEntryException.class).hasMessageContaining("never a remedy leg");
    }

    @Test
    void forfeiturePenaltyAndDamagesNeedADecision() {
        assertThatThrownBy(() -> entry(DEPOSIT_FORFEITURE, BUYER_DEPOSIT, null, null, null))
                .isInstanceOf(InvalidLedgerEntryException.class).hasMessageContaining("needs a remedy decision");
        assertThatThrownBy(() -> entry(CONTRACTUAL_PENALTY, MILESTONE_PAYMENT, id(), null, null))
                .isInstanceOf(InvalidLedgerEntryException.class);
        assertThatCode(() -> entry(DAMAGES_COMPENSATION, MILESTONE_PAYMENT, id(), id(), id())).doesNotThrowAnyException();
    }

    @Test
    void releaseAndRefundMayOrMayNotBeRemedyLegs() {
        assertThatCode(() -> entry(RELEASE_TO_SELLER, SELLER_DEPOSIT, null, null, null)).doesNotThrowAnyException();
        assertThatCode(() -> entry(RELEASE_TO_SELLER, SELLER_DEPOSIT, null, id(), id())).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingOrMalformedIds() {
        assertThatThrownBy(() -> LedgerEntry.create(null, id(), null, id(), LOCK_BUYER_DEPOSIT, BUYER_DEPOSIT,
                null, null, AMOUNT, NOW)).isInstanceOf(InvalidLedgerEntryException.class).hasMessageContaining("sourceEventId");
        assertThatThrownBy(() -> LedgerEntry.create(id(), "contract-1", null, id(), LOCK_BUYER_DEPOSIT, BUYER_DEPOSIT,
                null, null, AMOUNT, NOW)).isInstanceOf(InvalidLedgerEntryException.class).hasMessageContaining("not a UUID");
        assertThatThrownBy(() -> LedgerEntry.create(id(), id(), null, id(), null, BUYER_DEPOSIT,
                null, null, AMOUNT, NOW)).isInstanceOf(InvalidLedgerEntryException.class);
    }
}
