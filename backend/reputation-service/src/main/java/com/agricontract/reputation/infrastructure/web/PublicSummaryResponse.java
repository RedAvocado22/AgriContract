package com.agricontract.reputation.infrastructure.web;
import com.agricontract.reputation.application.dto.PublicSummaryResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
/** Deliberate HTTP allowlist, independent of domain records and application DTO evolution. */
public record PublicSummaryResponse(UUID userId, BigDecimal score, boolean locked, List<LockResponse> lockHistory) {
    public PublicSummaryResponse { lockHistory = List.copyOf(lockHistory); }
    public record LockResponse(Instant lockedAt, Instant lockedUntil, int lockDurationDays,
                               String penalizedRole, String status) {}
    public static PublicSummaryResponse from(PublicSummaryResult result) {
        return new PublicSummaryResponse(result.userId(), result.score(), result.locked(),
                result.lockHistory().stream().map(lock -> new LockResponse(lock.lockedAt(), lock.lockedUntil(),
                        lock.lockDurationDays(), lock.penalizedRole(), lock.status())).toList());
    }
}
