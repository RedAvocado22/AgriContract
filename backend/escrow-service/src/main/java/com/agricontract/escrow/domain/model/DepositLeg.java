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
    private final Instant createdAt;
    private Instant updatedAt;

    /** What a bank.lock_failed did to the leg. */
    public enum FailureOutcome {
        /** A retry of the same request is scheduled at nextAttemptAt. */
        RETRY_SCHEDULED,
        /** No retry is left; the leg stays FAILED (#247 reports it). */
        RETRIES_EXHAUSTED,
        /** The leg is already LOCKED: the bank ledger holds the money, a late failure changes nothing. */
        IGNORED_ALREADY_LOCKED
    }

    private DepositLeg(String legId, String contractId, DepositLegParty party, String userId, Money amount,
                       DepositLegStatus status, String requestEventId, String ledgerEntryId, int failedAttempts,
                       Instant nextAttemptAt, String lastFailureReason, Instant createdAt, Instant updatedAt) {
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
                                          String lastFailureReason, Instant createdAt, Instant updatedAt) {
        return new DepositLeg(legId, contractId, party, userId, amount, status, requestEventId, ledgerEntryId,
                failedAttempts, nextAttemptAt, lastFailureReason, createdAt, updatedAt);
    }

    /**
     * bank.lock_completed. Returns true when the leg became LOCKED now, false when it already was (duplicate
     * result). Also accepted after a failure: the retry succeeded.
     */
    public boolean markLocked(String ledgerEntryId, Instant now) {
        requireUuid("ledgerEntryId", ledgerEntryId);
        if (status == DepositLegStatus.LOCKED) {
            return false;
        }
        this.status = DepositLegStatus.LOCKED;
        this.ledgerEntryId = ledgerEntryId;
        this.nextAttemptAt = null;
        this.updatedAt = now;
        return true;
    }

    /** bank.lock_failed: counts the failure and schedules the next resend, or none when the schedule is used up. */
    public FailureOutcome markFailed(String reason, Instant now, RetrySchedule schedule) {
        if (status == DepositLegStatus.LOCKED) {
            return FailureOutcome.IGNORED_ALREADY_LOCKED;
        }
        this.status = DepositLegStatus.FAILED;
        this.failedAttempts += 1;
        this.lastFailureReason = reason == null ? null : reason.substring(0, Math.min(reason.length(), 500));
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
