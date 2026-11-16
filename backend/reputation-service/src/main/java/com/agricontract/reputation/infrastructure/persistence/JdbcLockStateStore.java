package com.agricontract.reputation.infrastructure.persistence;

import com.agricontract.reputation.application.port.LockStateStore;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcLockStateStore implements LockStateStore {
    private final JdbcTemplate jdbc;
    public JdbcLockStateStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public State acquire(UUID userId) {
        jdbc.update("""
                INSERT INTO user_lock_state (user_id,lock_revision,effective_locked_until,updated_at)
                VALUES (?,0,NULL,UTC_TIMESTAMP()) ON DUPLICATE KEY UPDATE user_id = user_id
                """, userId.toString());
        return jdbc.queryForObject("SELECT lock_revision,effective_locked_until FROM user_lock_state WHERE user_id=? FOR UPDATE",
                (rs, n) -> new State(rs.getLong(1), instant(rs.getObject(2, LocalDateTime.class))), userId.toString());
    }
    @Override public Instant now() {
        return instant(jdbc.queryForObject("SELECT UTC_TIMESTAMP()", LocalDateTime.class));
    }
    @Override public Instant effectiveUntil(UUID userId, Instant now) {
        return instant(jdbc.queryForObject("""
                SELECT MAX(l.locked_until) FROM lock_entry l WHERE l.user_id=? AND l.locked_until > ?
                AND NOT EXISTS (SELECT 1 FROM lock_override_event o WHERE o.lock_entry_id=l.entry_id
                    AND o.override_type='UNLOCK_EARLY' AND o.proposed_by <> o.approved_by)
                """, LocalDateTime.class, userId.toString(), time(now)));
    }
    @Override public List<DueLock> dueUsers(Instant cutoff, DueLock after, int limit) {
        var mapper = (org.springframework.jdbc.core.RowMapper<DueLock>) (rs, n) -> new DueLock(
                instant(rs.getObject("effective_locked_until", LocalDateTime.class)), UUID.fromString(rs.getString("user_id")));
        if (after == null) {
            return jdbc.query("""
                SELECT effective_locked_until,user_id FROM user_lock_state WHERE effective_locked_until <= ?
                ORDER BY effective_locked_until,user_id LIMIT ?
                """, mapper, time(cutoff), limit);
        }
        return jdbc.query("""
                SELECT effective_locked_until,user_id FROM user_lock_state WHERE effective_locked_until <= ?
                AND (effective_locked_until > ? OR (effective_locked_until = ? AND user_id > ?))
                ORDER BY effective_locked_until,user_id LIMIT ?
                """, mapper, time(cutoff), time(after.effectiveUntil()), time(after.effectiveUntil()),
                after.userId().toString(), limit);
    }
    @Override public boolean alreadyPublished(String kind, UUID businessId, UUID userId, String reason) {
        return jdbc.query("SELECT user_id,reason_code FROM lock_event_receipt WHERE transition_type=? AND business_id=?",
                (rs, n) -> {
                    if (!userId.toString().equals(rs.getString(1)) || !reason.equals(rs.getString(2))) {
                        throw new IllegalArgumentException("Conflicting lock transition replay");
                    }
                    return true;
                }, kind, businessId.toString()).stream().findFirst().orElse(false);
    }
    @Override public void remember(String kind, UUID businessId, UUID userId, String reason) {
        jdbc.update("INSERT INTO lock_event_receipt (transition_type,business_id,user_id,reason_code) VALUES (?,?,?,?)",
                kind, businessId.toString(), userId.toString(), reason);
    }
    @Override public UUID requireLock(UUID userId, UUID remedyDecisionId) {
        var rows = jdbc.query("SELECT source_event_id FROM lock_entry WHERE user_id=? AND remedy_decision_id=?",
                (rs, n) -> UUID.fromString(rs.getString(1)), userId.toString(), remedyDecisionId.toString());
        if (rows.size() != 1) {
            throw new IllegalArgumentException("Persisted lock does not match transition");
        }
        return rows.getFirst();
    }
    @Override public void requireOverride(UUID userId, UUID overrideId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM lock_override_event o JOIN lock_entry l ON l.entry_id=o.lock_entry_id
                WHERE o.event_id=? AND l.user_id=? AND o.override_type='UNLOCK_EARLY' AND o.proposed_by<>o.approved_by
                """, Integer.class, overrideId.toString(), userId.toString());
        if (count == null || count != 1) {
            throw new IllegalArgumentException("Persisted override does not match transition");
        }
    }
    @Override public void save(UUID userId, long revision, Instant until, Instant now) {
        jdbc.update("UPDATE user_lock_state SET lock_revision=?,effective_locked_until=?,updated_at=? WHERE user_id=?",
                revision, time(until), time(now), userId.toString());
    }
    private static Instant instant(LocalDateTime time) { return time == null ? null : time.toInstant(ZoneOffset.UTC); }
    private static LocalDateTime time(Instant time) { return time == null ? null : LocalDateTime.ofInstant(time, ZoneOffset.UTC); }
}
