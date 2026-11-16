package com.agricontract.reputation.application.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LockStateStore {
    record State(long revision, Instant effectiveUntil) {}
    record DueLock(Instant effectiveUntil, UUID userId) {}
    State acquire(UUID userId);
    Instant now();
    Instant effectiveUntil(UUID userId, Instant now);
    List<DueLock> dueUsers(Instant cutoff, DueLock after, int limit);
    boolean alreadyPublished(String kind, UUID businessId, UUID userId, String reason);
    void remember(String kind, UUID businessId, UUID userId, String reason);
    UUID requireLock(UUID userId, UUID remedyDecisionId);
    void requireOverride(UUID userId, UUID overrideId);
    void save(UUID userId, long revision, Instant effectiveUntil, Instant now);
}
