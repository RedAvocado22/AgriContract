package com.agricontract.reputation.domain.model;
import java.time.Instant;
public record LockHistoryEntry(Instant lockedAt, Instant lockedUntil, int lockDurationDays,
                               ResolutionFavors penalizedRole, Status status) {
    public enum Status { ACTIVE, EXPIRED, UNLOCKED_EARLY }
}
