package com.agricontract.contract.domain.model.milestone;

import com.agricontract.contract.domain.model.terms.MilestoneTerm;
import com.agricontract.contract.domain.model.terms.VndAmount;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MilestoneTest {
    private static final LocalDate DELIVERY = LocalDate.parse("2036-12-15");
    private static final LocalDate FUNDING_DATE = LocalDate.parse("2036-12-04");
    private static final Instant REQUESTED = Instant.parse("2036-12-04T01:00:00Z");

    private static Milestone planned(String quantity) {
        return Milestone.plan("m-1", "c-1", new MilestoneTerm(1, new BigDecimal(quantity), DELIVERY, 2), FUNDING_DATE);
    }

    @Test
    void aPlannedMilestone_waitsInCreated_withNoBatchFixedYet() {
        Milestone m = planned("1000");

        assertThat(m.status()).isEqualTo(MilestoneStatus.CREATED);
        assertThat(m.fundingStatus()).isEqualTo(FundingStatus.NOT_REQUESTED);
        assertThat(m.batchAmount()).isNull();
        assertThat(m.fundingDelayBusinessDays()).isZero();
    }

    @Test
    void fundingIsDue_fromItsDateOn_andOnlyWhileNotRequested() {
        Milestone m = planned("1000");

        assertThat(m.isFundingDue(FUNDING_DATE.minusDays(1))).isFalse();
        assertThat(m.isFundingDue(FUNDING_DATE)).isTrue();
        assertThat(m.isFundingDue(FUNDING_DATE.plusDays(10))).isTrue();
        m.requestFunding(VndAmount.parse("95000.00"), REQUESTED);
        assertThat(m.isFundingDue(FUNDING_DATE)).isFalse();
    }

    @Test
    void batchAmount_isRoundedOnceToTheWholeDong_halfUp() {
        // 1500.5 kg × 95,001.01 = 142,549,015.505 → 142,549,016 (D65).
        Milestone m = planned("1500.5");

        VndAmount batch = m.requestFunding(VndAmount.parse("95001.01"), REQUESTED);

        assertThat(batch.wire()).isEqualTo("142549016.00");
        assertThat(m.batchAmount()).isEqualTo(batch);
        assertThat(m.effectiveUnitPrice().wire()).isEqualTo("95001.01");
        assertThat(m.fundingRequestedAt()).isEqualTo(REQUESTED);
        assertThat(m.fundingStatus()).isEqualTo(FundingStatus.FUNDING_PENDING);
        assertThat(m.status()).as("no delivery obligation before the lock").isEqualTo(MilestoneStatus.CREATED);
    }

    @Test
    void batchAmount_belowHalfADong_isRoundedDown() {
        // 0.001 kg × 400.00 = 0.4 → 0, refused: escrow cannot lock nothing.
        Milestone m = planned("0.001");

        assertThatThrownBy(() -> m.requestFunding(VndAmount.parse("400.00"), REQUESTED))
                .isInstanceOf(InvalidMilestoneTransitionException.class);
        assertThat(m.fundingStatus()).isEqualTo(FundingStatus.NOT_REQUESTED);
    }

    @Test
    void fundingIsRequestedOnlyOnce() {
        Milestone m = planned("1000");
        m.requestFunding(VndAmount.parse("95000.00"), REQUESTED);

        assertThatThrownBy(() -> m.requestFunding(VndAmount.parse("95000.00"), REQUESTED))
                .isInstanceOf(InvalidMilestoneTransitionException.class);
    }

    @Test
    void theLock_startsTheDelivery_andKeepsTheDelay() {
        Milestone m = planned("1000");
        m.requestFunding(VndAmount.parse("95000.00"), REQUESTED);
        Instant locked = Instant.parse("2036-12-08T03:00:00Z");

        assertThat(m.recordFunded(locked, 2)).isTrue();

        assertThat(m.fundingStatus()).isEqualTo(FundingStatus.LOCKED);
        assertThat(m.status()).isEqualTo(MilestoneStatus.IN_PROGRESS);
        assertThat(m.fundingLockedAt()).isEqualTo(locked);
        assertThat(m.fundingDelayBusinessDays()).isEqualTo(2);
        assertThat(m.recordFunded(locked.plusSeconds(60), 3)).as("a repeated report changes nothing").isFalse();
        assertThat(m.fundingDelayBusinessDays()).isEqualTo(2);
    }

    @Test
    void aLockBeforeTheRequest_orWithoutARequest_isRefused() {
        Milestone notRequested = planned("1000");
        assertThatThrownBy(() -> notRequested.recordFunded(REQUESTED, 0))
                .isInstanceOf(InvalidMilestoneTransitionException.class);

        Milestone requested = planned("1000");
        requested.requestFunding(VndAmount.parse("95000.00"), REQUESTED);
        assertThatThrownBy(() -> requested.recordFunded(REQUESTED.minusSeconds(1), 0))
                .isInstanceOf(InvalidMilestoneTransitionException.class);
    }

    @Test
    void aFailure_isRecorded_andALaterLockAfterARetryIsStillAccepted() {
        Milestone m = planned("1000");
        m.requestFunding(VndAmount.parse("95000.00"), REQUESTED);

        assertThat(m.recordFundingFailed("INSUFFICIENT_FUNDS")).isTrue();
        assertThat(m.recordFundingFailed("INSUFFICIENT_FUNDS")).isFalse();
        assertThat(m.fundingStatus()).isEqualTo(FundingStatus.FUNDING_FAILED);
        assertThat(m.lastFundingFailureReason()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(m.status()).isEqualTo(MilestoneStatus.CREATED);

        assertThat(m.recordFunded(REQUESTED.plusSeconds(86_400 * 3), 3)).isTrue();
        assertThat(m.status()).isEqualTo(MilestoneStatus.IN_PROGRESS);
    }

    @Test
    void aFailureAfterTheLock_isRefused_andALongReasonIsClipped() {
        Milestone locked = planned("1000");
        locked.requestFunding(VndAmount.parse("95000.00"), REQUESTED);
        locked.recordFunded(REQUESTED, 0);
        assertThatThrownBy(() -> locked.recordFundingFailed("late")).isInstanceOf(InvalidMilestoneTransitionException.class);

        Milestone failing = planned("1000");
        failing.requestFunding(VndAmount.parse("95000.00"), REQUESTED);
        failing.recordFundingFailed("x".repeat(600));
        assertThat(failing.lastFundingFailureReason()).hasSize(Milestone.MAX_FAILURE_REASON);
    }

    @Test
    void theFundingDate_mustBeBeforeTheDeliveryDate() {
        MilestoneTerm term = new MilestoneTerm(1, new BigDecimal("1000"), DELIVERY, 2);

        assertThatThrownBy(() -> Milestone.plan("m-1", "c-1", term, DELIVERY))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
