package com.agricontract.escrow.domain.model;

import com.agricontract.escrow.domain.exception.InvalidDepositLegException;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

// One contract-level deposit to lock at activation (milestone-escrow design §2.3, §3.1). Escrow keeps the leg's
// state only; the money itself lives in bank-service ledger_entry. Table: deposit_leg (V9, results V10).
// Results (#102, D39): the bank ledger is the truth, so LOCKED is final; a failure schedules a resend of the same
// requestEventId until the RetrySchedule is used up.
@Getter
public final class DepositLeg {

    private final String legId;
    private final String contractId;
    private final DepositLegParty party;
    private final String userId;            // buyerId or sellerId from contract.signed
    private final Money amount;             // buyerDepositAmount / sellerDepositAmount from contract.signed
    private DepositLegStatus status;
    private final String requestEventId;    // eventId = sourceEventId of bank.lock_requested; a retry resends it (D27)
    private String ledgerEntryId;           // bank ledger row, set when LOCKED
    private int failedAttempts;             // bank.lock_failed results received
    private Instant nextAttemptAt;          // FAILED: when the retry is due; null when none is left
    private String lastFailureReason;
    private String returnRequestEventId;    // eventId = sourceEventId of the return command (#247); a retry resends it
    private String returnLedgerEntryId;     // bank ledger row of the return, set when RETURNED
    private final Instant createdAt;
    private Instant updatedAt;

    /** What a bank.lock_failed did to the leg. */
    public enum FailureOutcome {
        /** A retry of the same request is scheduled at nextAttemptAt. */
        RETRY_SCHEDULED,
        /** No retry is left; the leg stays FAILED (reported in escrow.deposit_lock_failed, #247). */
        RETRIES_EXHAUSTED,
        /** The leg is already LOCKED: the bank ledger holds the money, a late failure changes nothing. */
        IGNORED_ALREADY_LOCKED,
        /** The parties cancelled while this lock was in flight; it failed, so nothing was locked: CANCELLED. */
        CANCELLED,
        /** The leg was already cancelled; a late failure changes nothing. */
        IGNORED_CANCELLED
    }

    /** What the parties' cancel (contract.activation_refund_requested, D42) did to the leg. */
    public enum StopOutcome {
        /** The money was locked: its return command must be sent (status RETURN_REQUESTED). */
        RETURN_REQUESTED,
        /** A lock request is in flight: its result decides (status CANCEL_PENDING). */
        WAITING_FOR_LOCK_RESULT,
        /** Nothing was locked: the leg is CANCELLED, nothing to return. */
        CANCELLED,
        /** The leg is already on the cancel path (a redelivered request). */
        ALREADY_STOPPED
    }

    /** What a bank return result did to the leg. */
    public enum ReturnOutcome {
        RETURNED,
        DUPLICATE,
        RETRY_SCHEDULED,
        /** No resend is left: the money stays locked, OPERATOR must act (alert). */
        RETRIES_EXHAUSTED,
        /** Already RETURNED: a late failure changes nothing. */
        IGNORED_ALREADY_RETURNED
    }

    private DepositLeg(String legId, String contractId, DepositLegParty party, String userId, Money amount,
                       DepositLegStatus status, String requestEventId, String ledgerEntryId, int failedAttempts,
                       Instant nextAttemptAt, String lastFailureReason, String returnRequestEventId,
                       String returnLedgerEntryId, Instant createdAt, Instant updatedAt) {
        this.legId = legId;
        this.contractId = contractId;
        this.party = party;
        this.userId = userId;
        this.amount = amount;
        this.status = status;
        this.requestEventId = requestEventId;
        this.ledgerEntryId = ledgerEntryId;
        this.failedAttempts = failedAttempts;
        this.nextAttemptAt = nextAttemptAt;
        this.lastFailureReason = lastFailureReason;
        this.returnRequestEventId = returnRequestEventId;
        this.returnLedgerEntryId = returnLedgerEntryId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * New leg in REQUESTED, with a fresh legId and requestEventId.
     */
    public static DepositLeg request(String contractId, DepositLegParty party, String userId, Money amount,
                                     Instant now) {
        if (party == null)
            throw new InvalidDepositLegException("Party cannot be null");

        if (amount == null)
            throw new InvalidDepositLegException("Amount cannot be null");

        if (now == null)
            throw new InvalidDepositLegException("Now cannot be null");

        requireUuid("contractId", contractId);
        requireUuid("userId", userId);

        return new DepositLeg(
                UUID.randomUUID().toString(),
                contractId,
                party,
                userId,
                amount,
                DepositLegStatus.REQUESTED,
                UUID.randomUUID().toString(),
                null,
                0,
                null,
                null,
                null,
                null,
                now,
                now
        );
    }

    /**
     * Rebuilds a stored leg; no validation, the row was valid when written.
     */
    public static DepositLeg reconstitute(String legId, String contractId, DepositLegParty party, String userId,
                                          Money amount, DepositLegStatus status, String requestEventId,
                                          String ledgerEntryId, int failedAttempts, Instant nextAttemptAt,
                                          String lastFailureReason, String returnRequestEventId,
                                          String returnLedgerEntryId, Instant createdAt, Instant updatedAt) {
        return new DepositLeg(legId, contractId, party, userId, amount, status, requestEventId, ledgerEntryId,
                failedAttempts, nextAttemptAt, lastFailureReason, returnRequestEventId, returnLedgerEntryId,
                createdAt, updatedAt);
    }

    /**
     * bank.lock_completed. Returns true when the leg changed now, false when the money was already known locked
     * (duplicate result). Also accepted after a failure: the retry succeeded. The bank ledger is the truth, so a lock
     * that lands after the parties cancelled (CANCEL_PENDING, or CANCELLED) is returned at once: the leg goes straight
     * to RETURN_REQUESTED and the caller sends the return command.
     */
    public boolean markLocked(String ledgerEntryId, Instant now) {
        requireUuid("ledgerEntryId", ledgerEntryId);
        if (holdsLockedMoney()) {
            return false;
        }
        boolean cancelled = status == DepositLegStatus.CANCEL_PENDING || status == DepositLegStatus.CANCELLED;
        this.status = DepositLegStatus.LOCKED;
        this.ledgerEntryId = ledgerEntryId;
        this.nextAttemptAt = null;
        this.updatedAt = now;
        if (cancelled) {
            requestReturn(now);
        }
        return true;
    }

    /** bank.lock_failed: counts the failure and schedules the next resend, or none when the schedule is used up. */
    public FailureOutcome markFailed(String reason, Instant now, RetrySchedule schedule) {
        if (holdsLockedMoney()) {
            return FailureOutcome.IGNORED_ALREADY_LOCKED;
        }
        if (status == DepositLegStatus.CANCELLED) {
            return FailureOutcome.IGNORED_CANCELLED;
        }
        if (status == DepositLegStatus.CANCEL_PENDING) {
            this.status = DepositLegStatus.CANCELLED;
            this.lastFailureReason = clip(reason);
            this.updatedAt = now;
            return FailureOutcome.CANCELLED;
        }
        this.status = DepositLegStatus.FAILED;
        this.failedAttempts += 1;
        this.lastFailureReason = clip(reason);
        this.nextAttemptAt = schedule.nextAttempt(failedAttempts, now).orElse(null);
        this.updatedAt = now;
        return nextAttemptAt == null ? FailureOutcome.RETRIES_EXHAUSTED : FailureOutcome.RETRY_SCHEDULED;
    }

    /** A FAILED leg whose retry time has come. */
    public boolean isRetryDue(Instant now) {
        return status == DepositLegStatus.FAILED && nextAttemptAt != null && !now.isBefore(nextAttemptAt);
    }

    /** The same request is being sent again: back to REQUESTED until its result arrives. */
    public void retryRequested(Instant now) {
        if (!isRetryDue(now)) {
            throw new IllegalStateException("Leg " + legId + " has no retry due (" + status + ")");
        }
        this.status = DepositLegStatus.REQUESTED;
        this.nextAttemptAt = null;
        this.updatedAt = now;
    }

    public boolean isLocked() {
        return status == DepositLegStatus.LOCKED;
    }

    /**
     * contract.activation_refund_requested (D41/D42): the parties stop the contract. A LOCKED leg's money goes back
     * to its owner; a lock in flight waits for its result; a failed leg is cancelled; no lock is ever retried again.
     */
    public StopOutcome stopForRefund(Instant now) {
        switch (status) {
            case LOCKED -> {
                requestReturn(now);
                return StopOutcome.RETURN_REQUESTED;
            }
            case REQUESTED -> {
                this.status = DepositLegStatus.CANCEL_PENDING;
                this.updatedAt = now;
                return StopOutcome.WAITING_FOR_LOCK_RESULT;
            }
            case FAILED -> {
                this.status = DepositLegStatus.CANCELLED;
                this.nextAttemptAt = null;
                this.updatedAt = now;
                return StopOutcome.CANCELLED;
            }
            default -> {
                return StopOutcome.ALREADY_STOPPED;
            }
        }
    }

    /** bank.refund_completed / bank.release_completed for this leg's return. False for a duplicate result. */
    public boolean markReturned(String returnLedgerEntryId, Instant now) {
        requireUuid("returnLedgerEntryId", returnLedgerEntryId);
        if (status == DepositLegStatus.RETURNED) {
            return false;
        }
        requireReturnInProgress();
        this.status = DepositLegStatus.RETURNED;
        this.returnLedgerEntryId = returnLedgerEntryId;
        this.nextAttemptAt = null;
        this.updatedAt = now;
        return true;
    }

    /** bank.refund_failed / bank.release_failed: a resend of the same return command per the schedule. */
    public ReturnOutcome markReturnFailed(String reason, Instant now, RetrySchedule schedule) {
        if (status == DepositLegStatus.RETURNED) {
            return ReturnOutcome.IGNORED_ALREADY_RETURNED;
        }
        requireReturnInProgress();
        this.status = DepositLegStatus.RETURN_FAILED;
        this.failedAttempts += 1;
        this.lastFailureReason = clip(reason);
        this.nextAttemptAt = schedule.nextAttempt(failedAttempts, now).orElse(null);
        this.updatedAt = now;
        return nextAttemptAt == null ? ReturnOutcome.RETRIES_EXHAUSTED : ReturnOutcome.RETRY_SCHEDULED;
    }

    /** A RETURN_FAILED leg whose resend time has come. */
    public boolean isReturnRetryDue(Instant now) {
        return status == DepositLegStatus.RETURN_FAILED && nextAttemptAt != null && !now.isBefore(nextAttemptAt);
    }

    /** The same return command is being sent again. */
    public void returnRetryRequested(Instant now) {
        if (!isReturnRetryDue(now)) {
            throw new IllegalStateException("Leg " + legId + " has no return retry due (" + status + ")");
        }
        this.status = DepositLegStatus.RETURN_REQUESTED;
        this.nextAttemptAt = null;
        this.updatedAt = now;
    }

    /** Nothing more will happen to the money of this leg on the cancel path: never locked, or returned. */
    public boolean isRefundSettled() {
        return status == DepositLegStatus.CANCELLED || status == DepositLegStatus.RETURNED;
    }

    /** The bank holds (or held, then returned) this leg's money: LOCKED or on its way back. */
    private boolean holdsLockedMoney() {
        return status == DepositLegStatus.LOCKED || status == DepositLegStatus.RETURN_REQUESTED
                || status == DepositLegStatus.RETURN_FAILED || status == DepositLegStatus.RETURNED;
    }

    private void requestReturn(Instant now) {
        this.status = DepositLegStatus.RETURN_REQUESTED;
        this.returnRequestEventId = UUID.randomUUID().toString();
        this.failedAttempts = 0;
        this.nextAttemptAt = null;
        this.updatedAt = now;
    }

    private void requireReturnInProgress() {
        if (status != DepositLegStatus.RETURN_REQUESTED && status != DepositLegStatus.RETURN_FAILED) {
            throw new InvalidDepositLegException("Leg " + legId + " has no return in progress (" + status + ")");
        }
    }

    private static String clip(String reason) {
        return reason == null ? null : reason.substring(0, Math.min(reason.length(), 500));
    }

    /**
     * OPERATOR RetryDepositLock (contract.deposit_lock_retry_requested, D42): a leg whose lock failed starts a new
     * automatic retry cycle. Back to REQUESTED with the failure count reset, so the RetrySchedule applies again from
     * its first step; the same requestEventId is sent again (bank re-evaluates a refused request, D39). False, and
     * nothing changes, when the leg's lock has not failed (LOCKED, or still in its cycle).
     */
    public boolean restartLockCycle(Instant now) {
        if (!isLockFailed()) {
            return false;
        }
        this.status = DepositLegStatus.REQUESTED;
        this.failedAttempts = 0;
        this.nextAttemptAt = null;
        this.updatedAt = now;
        return true;
    }

    /** FAILED with no retry left: the lock of this leg failed for this retry cycle (#247). */
    public boolean isLockFailed() {
        return status == DepositLegStatus.FAILED && nextAttemptAt == null;
    }

    private static void requireUuid(String field, String value) {
        if (value == null) {
            throw new InvalidDepositLegException(field + " is required");
        }
        try {
            if (!UUID.fromString(value).toString().equals(value.toLowerCase())) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new InvalidDepositLegException(field + " is not a UUID: " + value);
        }
    }
}
