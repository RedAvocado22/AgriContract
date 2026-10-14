package com.agricontract.reputation.domain.model;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
public record ReputationSummary(UUID userId, BigDecimal score, boolean locked, List<LockHistoryEntry> lockHistory) {
    public ReputationSummary { lockHistory = List.copyOf(lockHistory); }
}
