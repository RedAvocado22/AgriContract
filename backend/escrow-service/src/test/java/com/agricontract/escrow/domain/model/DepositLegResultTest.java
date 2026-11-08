package com.agricontract.escrow.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** #102, D39: bank results on a leg. The bank ledger is the truth, so LOCKED is final. */
class DepositLegResultTest {
    private static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");
    private static final RetrySchedule SCHEDULE = new RetrySchedule(
            List.of(Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofHours(2)));
    private static final String LEDGER_ENTRY = UUID.randomUUID().toString();

    @Test
    void completedResultLocksTheLeg_onceOnly() {
        DepositLeg leg = requested();

        assertThat(leg.markLocked(LEDGER_ENTRY, NOW)).isTrue();
        assertThat(leg.getStatus()).isEqualTo(DepositLegStatus.LOCKED);
        assertThat(leg.getLedgerEntryId()).isEqualTo(LEDGER_ENTRY);

        assertThat(leg.markLocked(UUID.randomUUID().toString(), NOW.plusSeconds(1))).isFalse();
        assertThat(leg.getLedgerEntryId()).isEqualTo(LEDGER_ENTRY);
    }

    @Test
    void failuresFollowTheBackoff_thenNoRetryIsLeft() {
        DepositLeg leg = requested();

        assertThat(leg.markFailed("bank down", NOW, SCHEDULE)).isEqualTo(DepositLeg.FailureOutcome.RETRY_SCHEDULED);
        assertThat(leg.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        retry(leg, leg.getNextAttemptAt());
        assertThat(leg.markFailed("bank down", NOW, SCHEDULE)).isEqualTo(DepositLeg.FailureOutcome.RETRY_SCHEDULED);
        assertThat(leg.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
        retry(leg, leg.getNextAttemptAt());
        assertThat(leg.markFailed("bank down", NOW, SCHEDULE)).isEqualTo(DepositLeg.FailureOutcome.RETRY_SCHEDULED);
        assertThat(leg.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofHours(2)));
        retry(leg, leg.getNextAttemptAt());

        assertThat(leg.markFailed("still down", NOW, SCHEDULE))
                .isEqualTo(DepositLeg.FailureOutcome.RETRIES_EXHAUSTED);
        assertThat(leg.getStatus()).isEqualTo(DepositLegStatus.FAILED);
        assertThat(leg.getFailedAttempts()).isEqualTo(4);
        assertThat(leg.getNextAttemptAt()).isNull();
        assertThat(leg.getLastFailureReason()).isEqualTo("still down");
        assertThat(leg.isRetryDue(NOW.plus(Duration.ofDays(1)))).isFalse();
    }

    @Test
    void retrySucceedingAfterAFailure_locksTheLeg() {
        DepositLeg leg = requested();
        leg.markFailed("bank down", NOW, SCHEDULE);
        retry(leg, leg.getNextAttemptAt());

        assertThat(leg.markLocked(LEDGER_ENTRY, NOW)).isTrue();
        assertThat(leg.getStatus()).isEqualTo(DepositLegStatus.LOCKED);
        assertThat(leg.getNextAttemptAt()).isNull();
    }

    @Test
    void lateFailureForALockedLeg_isIgnored() {
        DepositLeg leg = requested();
        leg.markLocked(LEDGER_ENTRY, NOW);

        assertThat(leg.markFailed("old attempt", NOW.plusSeconds(5), SCHEDULE))
                .isEqualTo(DepositLeg.FailureOutcome.IGNORED_ALREADY_LOCKED);
        assertThat(leg.getStatus()).isEqualTo(DepositLegStatus.LOCKED);
        assertThat(leg.getFailedAttempts()).isZero();
    }

    @Test
    void retryIsDueOnlyAtItsTime_andOnlyForAFailedLeg() {
        DepositLeg leg = requested();
        assertThat(leg.isRetryDue(NOW)).isFalse();
        leg.markFailed("bank down", NOW, SCHEDULE);

        assertThat(leg.isRetryDue(NOW.plus(Duration.ofMinutes(4)))).isFalse();
        assertThat(leg.isRetryDue(NOW.plus(Duration.ofMinutes(5)))).isTrue();
        assertThatThrownBy(() -> leg.retryRequested(NOW)).isInstanceOf(IllegalStateException.class);

        leg.retryRequested(NOW.plus(Duration.ofMinutes(5)));
        assertThat(leg.getStatus()).isEqualTo(DepositLegStatus.REQUESTED);
        assertThat(leg.getNextAttemptAt()).isNull();
    }

    @Test
    void scheduleNeedsPositiveDurations() {
        assertThatThrownBy(() -> new RetrySchedule(List.of())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> new RetrySchedule(List.of(Duration.ZERO))).isInstanceOf(RuntimeException.class);
    }

    private static void retry(DepositLeg leg, Instant at) {
        leg.retryRequested(at);
    }

    private static DepositLeg requested() {
        return DepositLeg.request(UUID.randomUUID().toString(), DepositLegParty.BUYER, UUID.randomUUID().toString(),
                new Money(new BigDecimal("40000000.00")), NOW);
    }
}
