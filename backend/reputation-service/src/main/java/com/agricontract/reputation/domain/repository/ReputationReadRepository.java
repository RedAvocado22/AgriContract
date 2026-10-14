package com.agricontract.reputation.domain.repository;
import com.agricontract.reputation.domain.model.LockHistoryEntry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public interface ReputationReadRepository {
    long countCompleted(UUID userId);
    List<LockHistoryEntry> lockHistory(UUID userId, Instant now);
}
