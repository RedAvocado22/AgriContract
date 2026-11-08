package com.agricontract.contract.domain.model.activation;

import java.time.Instant;
import java.util.Objects;

/**
 * A SIGNED contract whose activation deposits could not be locked (escrow.deposit_lock_failed, #234, D42).
 *
 * <p>{@code depositLockFailedAt} is the first failure and never moves; {@code oneSidedCancelAllowedFrom} is fixed from
 * it when the first failure is recorded. A later failure (after an OPERATOR retry) only updates the {@code last*}
 * fields: the platform does not get more time to hold the parties' money because its fix did not work.
 */
public final class ActivationRecovery {

    public enum FailedLeg { BUYER, SELLER, BOTH }

    private static final int MAX_REASON = 500;

    private final String contractId;
    private final Instant depositLockFailedAt;
    private final Instant oneSidedCancelAllowedFrom;
    private FailedLeg lastFailedLeg;
    private String lastFailureReason;
    private Instant lastFailedAt;
    private int failureReports;

    private ActivationRecovery(String contractId, Instant depositLockFailedAt, Instant oneSidedCancelAllowedFrom,
                               FailedLeg lastFailedLeg, String lastFailureReason, Instant lastFailedAt,
                               int failureReports) {
        this.contractId = contractId;
        this.depositLockFailedAt = depositLockFailedAt;
        this.oneSidedCancelAllowedFrom = oneSidedCancelAllowedFrom;
        this.lastFailedLeg = lastFailedLeg;
        this.lastFailureReason = lastFailureReason;
        this.lastFailedAt = lastFailedAt;
        this.failureReports = failureReports;
    }

    /** The first escrow.deposit_lock_failed of the contract; the window end is computed by the caller's calendar. */
    public static ActivationRecovery firstFailure(String contractId, Instant failedAt, Instant oneSidedCancelAllowedFrom,
                                                  FailedLeg failedLeg, String reason) {
        Objects.requireNonNull(contractId, "contractId");
        Objects.requireNonNull(failedAt, "failedAt");
        Objects.requireNonNull(failedLeg, "failedLeg");
        if (oneSidedCancelAllowedFrom == null || !oneSidedCancelAllowedFrom.isAfter(failedAt)) {
            throw new IllegalArgumentException("oneSidedCancelAllowedFrom must be after the failure");
        }
        return new ActivationRecovery(contractId, failedAt, oneSidedCancelAllowedFrom, failedLeg, clip(reason),
                failedAt, 1);
    }

    public static ActivationRecovery reconstitute(String contractId, Instant depositLockFailedAt,
                                                  Instant oneSidedCancelAllowedFrom, FailedLeg lastFailedLeg,
                                                  String lastFailureReason, Instant lastFailedAt, int failureReports) {
        return new ActivationRecovery(contractId, depositLockFailedAt, oneSidedCancelAllowedFrom, lastFailedLeg,
                lastFailureReason, lastFailedAt, failureReports);
    }

    /** A later report (a retry cycle failed again): the first failure and the window stay as they are. */
    public void anotherFailure(Instant failedAt, FailedLeg failedLeg, String reason) {
        Objects.requireNonNull(failedAt, "failedAt");
        Objects.requireNonNull(failedLeg, "failedLeg");
        this.lastFailedLeg = failedLeg;
        this.lastFailureReason = clip(reason);
        this.lastFailedAt = failedAt;
        this.failureReports += 1;
    }

    /** From this instant one party's cancel request is enough (D42). */
    public boolean oneSidedCancelAllowed(Instant now) {
        return !now.isBefore(oneSidedCancelAllowedFrom);
    }

    private static String clip(String reason) {
        return reason == null ? null : reason.substring(0, Math.min(reason.length(), MAX_REASON));
    }

    public String contractId() { return contractId; }
    public Instant depositLockFailedAt() { return depositLockFailedAt; }
    public Instant oneSidedCancelAllowedFrom() { return oneSidedCancelAllowedFrom; }
    public FailedLeg lastFailedLeg() { return lastFailedLeg; }
    public String lastFailureReason() { return lastFailureReason; }
    public Instant lastFailedAt() { return lastFailedAt; }
    public int failureReports() { return failureReports; }
}
