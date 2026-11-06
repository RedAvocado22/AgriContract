package com.agricontract.reputation.infrastructure.persistence;

import com.agricontract.reputation.domain.model.LockHistoryEntry;
import com.agricontract.reputation.domain.model.ReputationReadProjection;
import com.agricontract.reputation.domain.model.ResolutionFavors;
import com.agricontract.reputation.domain.repository.ReputationReadRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcReputationReadRepository implements ReputationReadRepository {
    // One effective-lock definition for the aggregate projection and public history.
    private static final String LOCK_STATUS = """
            CASE WHEN EXISTS (SELECT 1 FROM lock_override_event o WHERE o.lock_entry_id = l.entry_id
                AND o.override_type = 'UNLOCK_EARLY' AND o.proposed_by <> o.approved_by)
                THEN 'UNLOCKED_EARLY'
                WHEN l.locked_until > :now THEN 'ACTIVE' ELSE 'EXPIRED' END
            """;
    private static final String SUMMARIES = """
            SELECT user_id, SUM(completed) AS completed, SUM(locks) AS locks, MAX(active) AS active
            FROM (
                SELECT buyer_id AS user_id, COUNT(*) AS completed, 0 AS locks, 0 AS active
                FROM completed_contract_fact WHERE buyer_id IN (:userIds) GROUP BY buyer_id
                UNION ALL
                SELECT seller_id AS user_id, COUNT(*) AS completed, 0 AS locks, 0 AS active
                FROM completed_contract_fact WHERE seller_id IN (:userIds) AND seller_id <> buyer_id
                GROUP BY seller_id
                UNION ALL
                SELECT l.user_id, 0 AS completed, COUNT(*) AS locks,
                    MAX(CASE WHEN (%s) = 'ACTIVE' THEN 1 ELSE 0 END) AS active
                FROM lock_entry l WHERE l.user_id IN (:userIds) GROUP BY l.user_id
            ) facts GROUP BY user_id
            """.formatted(LOCK_STATUS);
    private static final String HISTORY = """
            SELECT l.created_at, l.locked_until, l.lock_duration_days, l.penalized_role, (%s) AS status
            FROM lock_entry l WHERE l.user_id = :userId ORDER BY l.created_at DESC, l.entry_id DESC
            """.formatted(LOCK_STATUS);

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcReputationReadRepository(JdbcTemplate jdbc) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    @Override
    public List<ReputationReadProjection> publicSummaries(List<UUID> userIds, Instant now) {
        var parameters = Map.of("userIds", userIds.stream().map(UUID::toString).toList(),
                "now", databaseTime(now));
        var rows = jdbc.query(SUMMARIES, parameters, (rs, n) -> new ReputationReadProjection(
                UUID.fromString(rs.getString("user_id")),
                ReputationReadProjection.score(rs.getLong("completed"), rs.getLong("locks")),
                rs.getBoolean("active")));
        Map<UUID, ReputationReadProjection> byUser = new HashMap<>();
        rows.forEach(row -> byUser.put(row.userId(), row));
        // Missing facts say nothing about user existence; no user-service lookup is needed.
        return userIds.stream().map(id -> byUser.getOrDefault(id,
                new ReputationReadProjection(id, null, false))).toList();
    }

    @Override
    public List<LockHistoryEntry> lockHistory(UUID userId, Instant now) {
        return jdbc.query(HISTORY, Map.of("userId", userId.toString(),
                "now", databaseTime(now)), (rs, n) -> new LockHistoryEntry(
                rs.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                rs.getObject("locked_until", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                rs.getInt("lock_duration_days"), ResolutionFavors.valueOf(rs.getString("penalized_role")),
                LockHistoryEntry.Status.valueOf(rs.getString("status"))));
    }

    private static LocalDateTime databaseTime(Instant now) {
        // TIMESTAMP(0) locks compare identically to Instant at this floor; avoid driver rounding up.
        return LocalDateTime.ofInstant(now.truncatedTo(ChronoUnit.SECONDS), ZoneOffset.UTC);
    }
}
