package com.agricontract.reputation.application.usecase;

import com.agricontract.reputation.application.port.LockEventPublisher;
import com.agricontract.reputation.application.port.LockStateStore;
import com.agricontract.reputation.application.port.TransactionRunner;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * D53 integration boundary. The caller owns eligibility/approval and the insert-only ledger write.
 * Its callback MUST write through the same datasource, only inside this boundary, after the user mutex
 * is acquired. It must not publish or perform external effects. No production caller exists until #144/#269.
 */
@Service
public class LockEventLifecycle {
    // Wire vocabulary only, NOT an eligibility whitelist. Eligibility belongs to #144.
    private static final Set<String> BREACH_REASONS = Set.of("NON_DELIVERY", "SHORTFALL", "LATE_DELIVERY",
            "QUALITY_BELOW_COMMITTED", "SIDE_SELLING", "PRODUCTION_SHOCK_NON_FM", "FUNDING_FAILURE",
            "LATE_PAYMENT", "FAILURE_TO_RECEIVE", "WRONGFUL_REJECTION", "FORCE_MAJEURE", "MUTUAL_EXIT",
            "ACTIVATION_FAILURE");
    private final TransactionRunner transactions;
    private final LockStateStore states;
    private final LockEventPublisher events;

    public LockEventLifecycle(TransactionRunner transactions, LockStateStore states, LockEventPublisher events) {
        this.transactions = transactions;
        this.states = states;
        this.events = events;
    }

    public boolean lockCreated(UUID userId, UUID remedyDecisionId, String breachReasonCode,
                               String correlationId, Runnable appendLock) {
        if (!BREACH_REASONS.contains(Objects.requireNonNull(breachReasonCode)))
            throw new IllegalArgumentException("Unknown breach reason");
        return transition(userId, remedyDecisionId, breachReasonCode, correlationId, appendLock, true);
    }

    public boolean unlockedEarly(UUID userId, UUID overrideId, String correlationId, Runnable appendApprovedOverride) {
        return transition(userId, overrideId, "UNLOCKED_EARLY", correlationId, appendApprovedOverride, false);
    }

    private boolean transition(UUID userId, UUID businessId, String reason, String correlationId,
                               Runnable append, boolean locked) {
        Objects.requireNonNull(userId);
        Objects.requireNonNull(businessId);
        Objects.requireNonNull(append);
        if (correlationId == null || correlationId.isBlank()) throw new IllegalArgumentException("Correlation required");
        String kind = locked ? "LOCK" : "EARLY_UNLOCK";
        return transactions.inTransaction(() -> {
            var state = states.acquire(userId);
            if (states.alreadyPublished(kind, businessId, userId, reason)) return false;
            append.run();
            UUID cause;
            if (locked) cause = states.requireLock(userId, businessId);
            else { states.requireOverride(userId, businessId); cause = businessId; }
            Instant now = states.now();
            Instant until = states.effectiveUntil(userId, now);
            // A delayed creation with no active lock cannot be represented by reputation.locked (non-null expiry).
            // Leave it retryable for the owning use case to resolve; never invent a lock or a wire value.
            if (locked && until == null) throw new IllegalStateException("No effective lock for creation event");
            persist(userId, state, until, reason, now, correlationId, cause, locked);
            states.remember(kind, businessId, userId, reason);
            return true;
        });
    }

    public boolean expire(UUID userId, String correlationId) {
        Objects.requireNonNull(userId);
        if (correlationId == null || correlationId.isBlank()) throw new IllegalArgumentException("Correlation required");
        return transactions.inTransaction(() -> {
            var state = states.acquire(userId);
            Instant now = states.now(); // Read after any wait on the mutex, using the DB's UTC clock.
            if (state.effectiveUntil() == null || state.effectiveUntil().isAfter(now)) return false;
            if (states.effectiveUntil(userId, now) != null) return false;
            persist(userId, state, null, "EXPIRED", now, correlationId, null, false);
            return true;
        });
    }

    private void persist(UUID userId, LockStateStore.State state, Instant until, String reason, Instant now,
                         String correlationId, UUID cause, boolean locked) {
        long revision = Math.incrementExact(state.revision());
        states.save(userId, revision, until, now);
        events.publish(userId, locked, until, reason, revision, now, correlationId, cause);
    }
}
