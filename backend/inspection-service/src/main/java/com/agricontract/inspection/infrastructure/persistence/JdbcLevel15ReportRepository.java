package com.agricontract.inspection.infrastructure.persistence;

import com.agricontract.inspection.domain.model.*;
import com.agricontract.inspection.domain.repository.Level15ReportRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

@Repository
public class JdbcLevel15ReportRepository implements Level15ReportRepository {
    private final JdbcTemplate jdbc;
    public JdbcLevel15ReportRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final RowMapper<InspectionAssignment> ASSIGNMENT = (rs, row) -> new InspectionAssignment(
            rs.getString("commission_id"), rs.getString("contract_id"), rs.getString("milestone_id"),
            rs.getString("inspector_id"), rs.getTimestamp("assigned_at").toInstant(),
            rs.getTimestamp("ended_at") == null ? null : rs.getTimestamp("ended_at").toInstant(),
            rs.getString("assigned_by_service"), rs.getString("initiated_by_actor_id"));

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<InspectionAssignment> lockAssignment(String commissionId) {
        return jdbc.query("SELECT * FROM level1_5_inspection_assignment WHERE commission_id=? FOR UPDATE",
                ASSIGNMENT, commissionId).stream().findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(ReportIdentity identity, ReportEvidence evidence, Instant createdAt) {
        var assignment = identity.assignment();
        jdbc.update("""
                INSERT INTO inspection_report
                  (report_id,l15_commission_id,contract_id,milestone_id,inspector_id,tier,status,
                   content,normalized_result,report_file_hash,result_hash,report_hash,confirmed_at,created_at)
                VALUES (?,?,?,?,?,'LEVEL_1_5','CONFIRMED',?,?,?,?,?,?,?)
                """, identity.reportId(), assignment.commissionId(), assignment.contractId(), assignment.milestoneId(),
                assignment.inspectorId(), evidence.content(), evidence.normalizedResult(), evidence.reportFileHash(),
                evidence.resultHash(), evidence.reportHash(), Timestamp.from(evidence.confirmedAt()), Timestamp.from(createdAt));
    }

    @Override
    public Optional<Level15Report> find(String reportId) {
        return jdbc.query("SELECT * FROM inspection_report WHERE report_id=?", (rs, row) -> new Level15Report(
                rs.getString("report_id"), rs.getString("l15_commission_id"), rs.getString("contract_id"),
                rs.getString("milestone_id"), rs.getString("inspector_id"),
                new ReportEvidence(rs.getString("content"), rs.getString("normalized_result"),
                        rs.getString("report_file_hash"), rs.getString("result_hash"), rs.getString("report_hash"),
                        rs.getTimestamp("confirmed_at").toInstant()), rs.getTimestamp("created_at").toInstant()),
                reportId).stream().findFirst();
    }

    @Override
    public boolean hasConfirmedReport(String commissionId) {
        // Current read after the assignment lock: avoid stale repeatable-read snapshots on concurrent submissions.
        return !jdbc.queryForList("SELECT report_id FROM inspection_report WHERE l15_commission_id=? AND status='CONFIRMED' FOR UPDATE",
                String.class, commissionId).isEmpty();
    }

    @Override
    public List<InspectionAssignment> pending(String inspectorId) {
        return jdbc.query("""
                SELECT a.* FROM level1_5_inspection_assignment a
                WHERE a.inspector_id=? AND a.ended_at IS NULL
                  AND NOT EXISTS (SELECT 1 FROM inspection_report r
                                  WHERE r.l15_commission_id=a.commission_id AND r.status='CONFIRMED')
                ORDER BY a.assigned_at,a.commission_id
                """, ASSIGNMENT, inspectorId);
    }
}
