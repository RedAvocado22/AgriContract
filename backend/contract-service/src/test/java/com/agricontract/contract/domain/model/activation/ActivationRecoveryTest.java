package com.agricontract.contract.domain.model.activation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** D42: the first failure fixes the one-sided cancel window; later failures never move it. */
class ActivationRecoveryTest {
    private static final Instant FAILED = Instant.parse("2026-11-09T03:00:00Z");
    private static final Instant WINDOW = Instant.parse("2026-11-16T17:00:00Z");

    @Test
    void laterFailuresUpdateTheLatestReportOnly() {
        ActivationRecovery recovery = ActivationRecovery.firstFailure("c-1", FAILED, WINDOW,
                ActivationRecovery.FailedLeg.SELLER, "SELLER: SYSTEM_LOCK_ACTIVE");

        recovery.anotherFailure(FAILED.plus(Duration.ofDays(2)), ActivationRecovery.FailedLeg.BOTH, "x".repeat(600));

        assertThat(recovery.depositLockFailedAt()).isEqualTo(FAILED);
        assertThat(recovery.oneSidedCancelAllowedFrom()).isEqualTo(WINDOW);
        assertThat(recovery.lastFailedLeg()).isEqualTo(ActivationRecovery.FailedLeg.BOTH);
        assertThat(recovery.lastFailureReason()).hasSize(500);
        assertThat(recovery.failureReports()).isEqualTo(2);
    }

    @Test
    void oneSidedCancelIsAllowedFromTheWindowInstantOn() {
        ActivationRecovery recovery = ActivationRecovery.firstFailure("c-1", FAILED, WINDOW,
                ActivationRecovery.FailedLeg.BUYER, null);

        assertThat(recovery.oneSidedCancelAllowed(WINDOW.minusMillis(1))).isFalse();
        assertThat(recovery.oneSidedCancelAllowed(WINDOW)).isTrue();
    }

    @Test
    void theWindowMustEndAfterTheFailure() {
        assertThatThrownBy(() -> ActivationRecovery.firstFailure("c-1", FAILED, FAILED,
                ActivationRecovery.FailedLeg.BUYER, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
