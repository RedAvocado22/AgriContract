package com.agricontract.contract.domain.model.milestone;

import com.agricontract.contract.domain.model.terms.MilestoneTerm;
import com.agricontract.contract.domain.model.terms.VndAmount;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One delivery batch of an ACTIVE contract (milestone-escrow design §2.2): its own aggregate, loaded and saved on its
 * own, never as a child of Contract. Created for every MilestoneTerm when the contract becomes ACTIVE.
 *
 * <p>Funding (D63, D64): on {@code fundingScheduledDate} contract-service fixes {@code batchAmount} and asks escrow to
 * lock it ({@link #requestFunding}). Each milestone follows its own date and never waits for the previous one.
 * The milestone stays CREATED until escrow reports the lock ({@link #recordFunded}); only then does the seller's
 * delivery obligation start, and a late lock moves the deadline by {@code fundingDelayBusinessDays}.
 */
public final class Milestone {
    public static final int MAX_FAILURE_REASON = 500;

    private final String milestoneId;
    private final String contractId;
    private final int milestoneIndex;
    private final BigDecimal committedQuantity;
    private final LocalDate expectedDeliveryDate;
    private final int graceDays;
    private final LocalDate fundingScheduledDate;
    private MilestoneStatus status;
    private FundingStatus fundingStatus;
    private VndAmount effectiveUnitPrice;
    private VndAmount batchAmount;
    private Instant fundingRequestedAt;
    private Instant fundingLockedAt;
    private int fundingDelayBusinessDays;
    private String lastFundingFailureReason;

    private Milestone(String milestoneId, String contractId, int milestoneIndex, BigDecimal committedQuantity,
                      LocalDate expectedDeliveryDate, int graceDays, LocalDate fundingScheduledDate,
                      MilestoneStatus status, FundingStatus fundingStatus, VndAmount effectiveUnitPrice,
                      VndAmount batchAmount, Instant fundingRequestedAt, Instant fundingLockedAt,
                      int fundingDelayBusinessDays, String lastFundingFailureReason) {
        this.milestoneId = milestoneId;
        this.contractId = contractId;
        this.milestoneIndex = milestoneIndex;
        this.committedQuantity = committedQuantity;
        this.expectedDeliveryDate = expectedDeliveryDate;
        this.graceDays = graceDays;
        this.fundingScheduledDate = fundingScheduledDate;
        this.status = status;
        this.fundingStatus = fundingStatus;
        this.effectiveUnitPrice = effectiveUnitPrice;
        this.batchAmount = batchAmount;
        this.fundingRequestedAt = fundingRequestedAt;
        this.fundingLockedAt = fundingLockedAt;
        this.fundingDelayBusinessDays = fundingDelayBusinessDays;
        this.lastFundingFailureReason = lastFundingFailureReason;
    }

    /**
     * A new milestone of a contract that just became ACTIVE, from its signed term. fundingScheduledDate is computed by
     * the caller on the Vietnam calendar: expectedDeliveryDate minus fundingLeadBusinessDays business days.
     */
    public static Milestone plan(String milestoneId, String contractId, MilestoneTerm term,
                                 LocalDate fundingScheduledDate) {
        Objects.requireNonNull(milestoneId, "milestoneId");
        Objects.requireNonNull(contractId, "contractId");
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(fundingScheduledDate, "fundingScheduledDate");
        if (!fundingScheduledDate.isBefore(term.expectedDeliveryDate())) {
            throw new IllegalArgumentException("fundingScheduledDate must be before expectedDeliveryDate");
        }
        return new Milestone(milestoneId, contractId, term.milestoneIndex(), term.committedQuantity(),
                term.expectedDeliveryDate(), term.graceDays(), fundingScheduledDate, MilestoneStatus.CREATED,
                FundingStatus.NOT_REQUESTED, null, null, null, null, 0, null);
    }

    public static Milestone reconstitute(String milestoneId, String contractId, int milestoneIndex,
                                         BigDecimal committedQuantity, LocalDate expectedDeliveryDate, int graceDays,
                                         LocalDate fundingScheduledDate, MilestoneStatus status,
                                         FundingStatus fundingStatus, VndAmount effectiveUnitPrice,
                                         VndAmount batchAmount, Instant fundingRequestedAt, Instant fundingLockedAt,
                                         int fundingDelayBusinessDays, String lastFundingFailureReason) {
        return new Milestone(milestoneId, contractId, milestoneIndex, committedQuantity, expectedDeliveryDate,
                graceDays, fundingScheduledDate, status, fundingStatus, effectiveUnitPrice, batchAmount,
                fundingRequestedAt, fundingLockedAt, fundingDelayBusinessDays, lastFundingFailureReason);
    }

    /** True from fundingScheduledDate on (ICT date), while funding has not been requested. */
    public boolean isFundingDue(LocalDate today) {
        return fundingStatus == FundingStatus.NOT_REQUESTED && !today.isBefore(fundingScheduledDate);
    }

    /**
     * Fixes batchAmount = committedQuantity × effectiveUnitPrice, rounded once to the whole đồng (D65), and moves to
     * FUNDING_PENDING. Called once; the caller writes milestone.funding_requested in the same transaction.
     */
    public VndAmount requestFunding(VndAmount unitPrice, Instant requestedAt) {
        Objects.requireNonNull(unitPrice, "unitPrice");
        Objects.requireNonNull(requestedAt, "requestedAt");
        if (fundingStatus != FundingStatus.NOT_REQUESTED) {
            throw new InvalidMilestoneTransitionException("Milestone " + milestoneId + " funding was already requested ("
                    + fundingStatus + ")");
        }
        VndAmount amount = VndAmount.ofDong(committedQuantity.multiply(unitPrice.value()));
        if (amount.value().signum() <= 0) {
            throw new InvalidMilestoneTransitionException("Milestone " + milestoneId + " batch amount rounds to 0 VND");
        }
        this.effectiveUnitPrice = unitPrice;
        this.batchAmount = amount;
        this.fundingRequestedAt = requestedAt;
        this.fundingStatus = FundingStatus.FUNDING_PENDING;
        return amount;
    }

    /**
     * escrow.milestone_funded: LOCKED, and CREATED becomes IN_PROGRESS. delayBusinessDays is counted by the caller
     * from fundingRequestedAt to lockedAt on the Vietnam calendar. Returns false for a milestone already LOCKED (a
     * repeated report). A lock after FUNDING_FAILED is accepted: escrow retried and the buyer paid.
     */
    public boolean recordFunded(Instant lockedAt, int delayBusinessDays) {
        Objects.requireNonNull(lockedAt, "lockedAt");
        if (fundingStatus == FundingStatus.LOCKED) {
            return false;
        }
        if (fundingStatus != FundingStatus.FUNDING_PENDING && fundingStatus != FundingStatus.FUNDING_FAILED) {
            throw new InvalidMilestoneTransitionException("Milestone " + milestoneId + " cannot be funded in "
                    + fundingStatus);
        }
        if (lockedAt.isBefore(fundingRequestedAt)) {
            throw new InvalidMilestoneTransitionException("Milestone " + milestoneId + " locked before it was requested");
        }
        if (delayBusinessDays < 0) {
            throw new IllegalArgumentException("delayBusinessDays must not be negative");
        }
        this.fundingStatus = FundingStatus.LOCKED;
        this.fundingLockedAt = lockedAt;
        this.fundingDelayBusinessDays = delayBusinessDays;
        if (status == MilestoneStatus.CREATED) {
            this.status = MilestoneStatus.IN_PROGRESS;
        }
        return true;
    }

    /**
     * escrow.milestone_funding_failed: FUNDING_FAILED with the reason. Returns false when already FUNDING_FAILED. The
     * cure window and the notifications are #239.
     */
    public boolean recordFundingFailed(String reason) {
        if (fundingStatus == FundingStatus.FUNDING_FAILED) {
            return false;
        }
        if (fundingStatus != FundingStatus.FUNDING_PENDING) {
            throw new InvalidMilestoneTransitionException("Milestone " + milestoneId + " funding cannot fail in "
                    + fundingStatus);
        }
        this.fundingStatus = FundingStatus.FUNDING_FAILED;
        this.lastFundingFailureReason = reason == null || reason.length() <= MAX_FAILURE_REASON ? reason
                : reason.substring(0, MAX_FAILURE_REASON);
        return true;
    }

    public String milestoneId() {
        return milestoneId;
    }

    public String contractId() {
        return contractId;
    }

    public int milestoneIndex() {
        return milestoneIndex;
    }

    public BigDecimal committedQuantity() {
        return committedQuantity;
    }

    public LocalDate expectedDeliveryDate() {
        return expectedDeliveryDate;
    }

    public int graceDays() {
        return graceDays;
    }

    public LocalDate fundingScheduledDate() {
        return fundingScheduledDate;
    }

    public MilestoneStatus status() {
        return status;
    }

    public FundingStatus fundingStatus() {
        return fundingStatus;
    }

    public VndAmount effectiveUnitPrice() {
        return effectiveUnitPrice;
    }

    public VndAmount batchAmount() {
        return batchAmount;
    }

    public Instant fundingRequestedAt() {
        return fundingRequestedAt;
    }

    public Instant fundingLockedAt() {
        return fundingLockedAt;
    }

    public int fundingDelayBusinessDays() {
        return fundingDelayBusinessDays;
    }

    public String lastFundingFailureReason() {
        return lastFundingFailureReason;
    }
}
