package com.agricontract.escrow.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agricontract.escrow.domain.exception.InvalidDepositLegException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** #247, D41/D42: the parties cancel; locked money goes back, nothing is locked again. */
class DepositLegReturnTest {
    private static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");
    private static final RetrySchedule SCHEDULE = new RetrySchedule(List.of(Duration.ofMinutes(5), Duration.ofMinutes(30)));

    @Test
    void lockedLeg_requestsItsReturn_withAFreshCommandId() {
        DepositLeg leg = locked();

        assertThat(leg.stopForRefund(NOW)).isEqualTo(DepositLeg.StopOutcome.RETURN_REQUESTED);
        assertThat(leg.getStatus()).isEqualTo(DepositLegStatus.RETURN_REQUESTED);
        assertThat(leg.getReturnRequestEventId()).isNotNull().isNotEqualTo(leg.getRequestEventId());
        assertThat(leg.getFailedAttempts()).isZero();
        assertThat(leg.stopForRefund(NOW)).as("a redelivered cancel").isEqualTo(DepositLeg.StopOutcome.ALREADY_STOPPED);
    }

    @Test
    void failedLeg_isCancelled_andNeverRetriedAgain() {
        DepositLeg leg = requested();
        leg.markFailed("bank down", NOW, SCHEDULE);

        assertThat(leg.stopForRefund(NOW)).isEqualTo(DepositLeg.StopOutcome.CANCELLED);
        assertThat(leg.isRefundSettled()).isTrue();
        assertThat(leg.isRetryDue(NOW.plus(Duration.ofDays(1)))).isFalse();
        assertThat(leg.restartLockCycle(NOW)).isFalse();
        assertThat(leg.markFailed("late", NOW, SCHEDULE)).isEqualTo(DepositLeg.FailureOutcome.IGNORED_CANCELLED);
    }

    @Test
    void lockInFlight_waits_thenItsResultDecides() {
        DepositLeg failing = requested();
        assertThat(failing.stopForRefund(NOW)).isEqualTo(DepositLeg.StopOutcome.WAITING_FOR_LOCK_RESULT);
        assertThat(failing.isRefundSettled()).isFalse();
        assertThat(failing.markFailed("bank down", NOW, SCHEDULE)).isEqualTo(DepositLeg.FailureOutcome.CANCELLED);
        assertThat(failing.getStatus()).isEqualTo(DepositLegStatus.CANCELLED);

        DepositLeg locking = requested();
        locking.stopForRefund(NOW);
        assertThat(locking.markLocked(UUID.randomUUID().toString(), NOW)).isTrue();
        assertThat(locking.getStatus()).as("the bank holds the money: give it back").isEqualTo(DepositLegStatus.RETURN_REQUESTED);
        assertThat(locking.getReturnRequestEventId()).isNotNull();
    }

    @Test
    void returnConfirmed_once() {
        DepositLeg leg = locked();
        leg.stopForRefund(NOW);
        String entry = UUID.randomUUID().toString();

        assertThat(leg.markReturned(entry, NOW)).isTrue();
        assertThat(leg.getReturnLedgerEntryId()).isEqualTo(entry);
        assertThat(leg.isRefundSettled()).isTrue();
        assertThat(leg.markReturned(UUID.randomUUID().toString(), NOW)).isFalse();
        assertThat(leg.getReturnLedgerEntryId()).isEqualTo(entry);
        assertThat(leg.markReturnFailed("late", NOW, SCHEDULE))
                .isEqualTo(DepositLeg.ReturnOutcome.IGNORED_ALREADY_RETURNED);
    }

    @Test
    void refusedReturn_isResentPerTheSchedule_thenStaysFailed() {
        DepositLeg leg = locked();
        leg.stopForRefund(NOW);

        assertThat(leg.markReturnFailed("bank down", NOW, SCHEDULE)).isEqualTo(DepositLeg.ReturnOutcome.RETRY_SCHEDULED);
        assertThat(leg.isReturnRetryDue(NOW.plus(Duration.ofMinutes(5)))).isTrue();
        leg.returnRetryRequested(NOW.plus(Duration.ofMinutes(5)));
        assertThat(leg.getStatus()).isEqualTo(DepositLegStatus.RETURN_REQUESTED);
        leg.markReturnFailed("bank down", NOW, SCHEDULE);
        leg.returnRetryRequested(NOW.plus(Duration.ofMinutes(30)));

        assertThat(leg.markReturnFailed("still down", NOW, SCHEDULE))
                .isEqualTo(DepositLeg.ReturnOutcome.RETRIES_EXHAUSTED);
        assertThat(leg.getStatus()).isEqualTo(DepositLegStatus.RETURN_FAILED);
        assertThat(leg.isRefundSettled()).as("money still locked").isFalse();
        assertThat(leg.isReturnRetryDue(NOW.plus(Duration.ofDays(1)))).isFalse();
    }

    @Test
    void aReturnResultForALegWithNoReturn_isRefused() {
        assertThatThrownBy(() -> locked().markReturned(UUID.randomUUID().toString(), NOW))
                .isInstanceOf(InvalidDepositLegException.class);
    }

    @Test
    void refundIsSettledOnlyWhenEveryLegIsCancelledOrReturned() {
        DepositLeg buyer = locked();
        DepositLeg seller = requested();
        seller.markFailed("down", NOW, SCHEDULE);
        buyer.stopForRefund(NOW);
        seller.stopForRefund(NOW);
        ActivationDeposits deposits = new ActivationDeposits(List.of(buyer, seller));
        assertThat(deposits.refundSettled()).isFalse();

        buyer.markReturned(UUID.randomUUID().toString(), NOW);

        assertThat(deposits.refundSettled()).isTrue();
        assertThat(deposits.allLocked()).isFalse();
        assertThat(deposits.lockFailed()).as("no second deposit_lock_failed").isFalse();
    }

    private static DepositLeg locked() {
        DepositLeg leg = requested();
        leg.markLocked(UUID.randomUUID().toString(), NOW);
        return leg;
    }

    private static DepositLeg requested() {
        return DepositLeg.request(UUID.randomUUID().toString(), DepositLegParty.BUYER, UUID.randomUUID().toString(),
                new Money(new BigDecimal("1000000.00")), NOW);
    }
}
