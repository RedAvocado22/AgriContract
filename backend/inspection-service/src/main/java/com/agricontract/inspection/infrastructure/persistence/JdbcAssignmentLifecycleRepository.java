package com.agricontract.inspection.infrastructure.persistence;

import com.agricontract.inspection.domain.model.TimedAssignment;
import com.agricontract.inspection.domain.repository.AssignmentLifecycleRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class JdbcAssignmentLifecycleRepository implements AssignmentLifecycleRepository {
    private final JdbcTemplate jdbc;
    private static final RowMapper<TimedAssignment> ROW = (rs, n) -> new TimedAssignment(
            rs.getString("commission_id"), rs.getString("contract_id"), rs.getString("milestone_id"),
            rs.getString("inspector_id"), rs.getTimestamp("assigned_at").toInstant(),
            instant(rs.getTimestamp("ended_at")), (Integer) rs.getObject("report_due_business_days"),
            instant(rs.getTimestamp("report_due_at")), rs.getString("initiated_by_actor_id"));
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(String contractId, String milestoneId) {
        // Upsert takes an exclusive lock directly; INSERT IGNORE could require a shared-to-exclusive upgrade.
        jdbc.update("INSERT INTO level1_5_assignment_lock (contract_id, milestone_id) VALUES (?, ?) ON DUPLICATE KEY UPDATE contract_id=VALUES(contract_id)",
                contractId, milestoneId);
        jdbc.queryForList("SELECT contract_id FROM level1_5_assignment_lock WHERE contract_id=? AND milestone_id=? FOR UPDATE",
                contractId, milestoneId);
    }

    @Override
    public Optional<TimedAssignment> active(String contractId, String milestoneId) {
        // Locking read sees the latest committed row even under MySQL REPEATABLE READ.
        return jdbc.query("SELECT * FROM level1_5_inspection_assignment WHERE contract_id=? AND milestone_id=? AND ended_at IS NULL FOR UPDATE",
                ROW, contractId, milestoneId).stream().findFirst();
    }

    @Override
    public Optional<TimedAssignment> find(String commissionId) {
        return jdbc.query("SELECT * FROM level1_5_inspection_assignment WHERE commission_id=?", ROW, commissionId)
                .stream().findFirst();
    }

    @Override
    public Set<String> previousInspectors(String contractId, String milestoneId) {
        return new HashSet<>(jdbc.queryForList("SELECT inspector_id FROM level1_5_inspection_assignment WHERE contract_id=? AND milestone_id=? FOR UPDATE",
                String.class, contractId, milestoneId));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(TimedAssignment a) {
        jdbc.update("""
                INSERT INTO level1_5_inspection_assignment
                (commission_id,contract_id,milestone_id,inspector_id,assigned_at,assigned_by_service,
                 initiated_by_actor_id,report_due_business_days,report_due_at)
                VALUES (?,?,?,?,?,'contract-service',?,?,?)
                """, a.commissionId(), a.contractId(), a.milestoneId(), a.inspectorId(), Timestamp.from(a.assignedAt()),
                a.initiatedByActorId(), a.reportDueBusinessDays(), Timestamp.from(a.reportDueAt()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean end(String id, Instant at) {
        return jdbc.update("UPDATE level1_5_inspection_assignment SET ended_at=? WHERE commission_id=? AND ended_at IS NULL",
                Timestamp.from(at), id) == 1;
    }

    @Override
    public List<TimedAssignment> overdue(Instant now, int limit) {
        return jdbc.query("SELECT * FROM level1_5_inspection_assignment WHERE active_slot=1 AND report_due_at<=? ORDER BY report_due_at,commission_id LIMIT ?",
                ROW, Timestamp.from(now), limit);
    }
}
