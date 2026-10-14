package com.agricontract.reputation.infrastructure.persistence;
import com.agricontract.reputation.domain.model.LockHistoryEntry;
import com.agricontract.reputation.domain.model.ResolutionFavors;
import com.agricontract.reputation.domain.repository.ReputationReadRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class JdbcReputationReadRepository implements ReputationReadRepository {
    private final JdbcTemplate jdbc;
    public JdbcReputationReadRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override
    public long countCompleted(UUID userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM completed_contract_fact WHERE buyer_id = ? OR seller_id = ?",
                Long.class, userId.toString(), userId.toString());
    }
    @Override
    public List<LockHistoryEntry> lockHistory(UUID userId, Instant now) {
        return jdbc.query("""
                SELECT l.created_at, l.locked_until, l.lock_duration_days, l.penalized_role,
                    EXISTS (SELECT 1 FROM lock_override_event o WHERE o.lock_entry_id = l.entry_id
                        AND o.override_type = 'UNLOCK_EARLY' AND o.proposed_by <> o.approved_by) AS overridden
                FROM lock_entry l WHERE l.user_id = ? ORDER BY l.created_at DESC, l.entry_id DESC
                """, (rs, n) -> {
            Instant until = rs.getObject("locked_until", LocalDateTime.class).toInstant(ZoneOffset.UTC);
            var status = rs.getBoolean("overridden") ? LockHistoryEntry.Status.UNLOCKED_EARLY
                    : until.isAfter(now) ? LockHistoryEntry.Status.ACTIVE : LockHistoryEntry.Status.EXPIRED;
            return new LockHistoryEntry(rs.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                    until, rs.getInt("lock_duration_days"), ResolutionFavors.valueOf(rs.getString("penalized_role")), status);
        }, userId.toString());
    }
}
