package com.agricontract.inspection.infrastructure.persistence;

import com.agricontract.inspection.application.port.AssignmentWaitingRepository;
import com.agricontract.inspection.domain.model.WaitingAssignment;
import com.agricontract.inspection.domain.model.WaitingAssignment.Key;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class JdbcAssignmentWaitingRepository implements AssignmentWaitingRepository {
    private final JdbcTemplate jdbc;
    private static final RowMapper<WaitingAssignment> ROW = (rs, n) -> new WaitingAssignment(
            rs.getString("contract_id"), rs.getString("milestone_id"),
            WaitingAssignment.Reason.valueOf(rs.getString("reason")), rs.getInt("report_due_business_days"),
            rs.getString("assigned_by_service"), rs.getString("initiated_by_actor_id"),
            rs.getTimestamp("waiting_since").toInstant(), rs.getTimestamp("last_attempt_at").toInstant(),
            rs.getObject("last_reminder_on", LocalDate.class));

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean tryReserve(WaitingAssignment waiting) {
        try {
            insert(waiting);
            return true;
        } catch (DuplicateKeyException duplicate) {
            // The composite primary key is the table's only unique constraint.
            return false;
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(WaitingAssignment w) {
        jdbc.update("""
                INSERT INTO level1_5_assignment_waiting
                (contract_id,milestone_id,reason,report_due_business_days,assigned_by_service,
                 initiated_by_actor_id,waiting_since,last_attempt_at,last_reminder_on)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, w.contractId(), w.milestoneId(), w.reason().name(), w.reportDueBusinessDays(),
                w.assignedByService(), w.initiatedByActorId(), Timestamp.from(w.waitingSince()),
                Timestamp.from(w.lastAttemptAt()), date(w.lastReminderOn()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void started(Key key, Instant at) {
        int rows = jdbc.update("UPDATE level1_5_assignment_waiting SET waiting_since=?,last_attempt_at=? WHERE contract_id=? AND milestone_id=?",
                Timestamp.from(at), Timestamp.from(at), key.contractId(), key.milestoneId());
        if (rows != 1) {
            throw new IllegalStateException("Waiting request disappeared during creation");
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<WaitingAssignment> lockSkippingLocked(Key key) {
        return jdbc.query("SELECT * FROM level1_5_assignment_waiting WHERE contract_id=? AND milestone_id=? FOR UPDATE SKIP LOCKED",
                ROW, key.contractId(), key.milestoneId()).stream().findFirst();
    }

    @Override
    public List<Key> pendingAfter(Key after, int limit) {
        RowMapper<Key> keys = (rs, n) -> new Key(rs.getString("contract_id"), rs.getString("milestone_id"));
        if (after == null) {
            return jdbc.query("SELECT contract_id,milestone_id FROM level1_5_assignment_waiting ORDER BY contract_id,milestone_id LIMIT ?",
                    keys, limit);
        }
        return jdbc.query("""
                SELECT contract_id,milestone_id FROM level1_5_assignment_waiting
                WHERE contract_id>? OR (contract_id=? AND milestone_id>?)
                ORDER BY contract_id,milestone_id LIMIT ?
                """, keys, after.contractId(), after.contractId(), after.milestoneId(), limit);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void attempted(Key key, Instant at, LocalDate lastReminderOn) {
        int rows = jdbc.update("UPDATE level1_5_assignment_waiting SET last_attempt_at=?,last_reminder_on=? WHERE contract_id=? AND milestone_id=?",
                Timestamp.from(at), date(lastReminderOn), key.contractId(), key.milestoneId());
        if (rows != 1) {
            throw new IllegalStateException("Waiting request disappeared during retry");
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void delete(Key key) {
        int rows = jdbc.update("DELETE FROM level1_5_assignment_waiting WHERE contract_id=? AND milestone_id=?",
                key.contractId(), key.milestoneId());
        if (rows != 1) {
            throw new IllegalStateException("Waiting request disappeared during assignment");
        }
    }

    private static Date date(LocalDate date) { return date == null ? null : Date.valueOf(date); }
}
