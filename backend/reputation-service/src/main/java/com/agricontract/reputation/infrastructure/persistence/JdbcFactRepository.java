package com.agricontract.reputation.infrastructure.persistence;

import com.agricontract.reputation.application.exception.ImmutableFactConflictException;
import com.agricontract.reputation.domain.model.*;
import com.agricontract.reputation.domain.repository.FactRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class JdbcFactRepository implements FactRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    public JdbcFactRepository(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(manager);
        // Insert commits before returning to the listener. Collision reads use a fresh
        // transaction, avoiding a stale repeatable-read snapshot after a concurrent insert.
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    @Override
    public AppendResult append(CompletedContractFact f) {
        return insertOrCompare(() -> jdbc.update("""
                INSERT INTO completed_contract_fact (contract_id,buyer_id,seller_id,settled_at,source_event_id)
                VALUES (?,?,?,?,?)
                """, f.contractId().toString(), f.buyerId().toString(), f.sellerId().toString(),
                LocalDateTime.ofInstant(f.settledAt(), ZoneOffset.UTC), f.sourceEventId().toString()),
                () -> {
                    var rows = jdbc.query("""
                            SELECT * FROM completed_contract_fact WHERE contract_id = ? OR source_event_id = ?
                            """, (rs, n) -> completed(rs), f.contractId().toString(), f.sourceEventId().toString());
                    return rows.size() == 1 && rows.getFirst().sameContent(f);
                });
    }
    @Override
    public AppendResult append(DisputeOutcomeFact f) {
        return insertOrCompare(() -> jdbc.update("""
                INSERT INTO dispute_outcome_fact
                (milestone_id,contract_id,flagged_by_user_id,resolution_favors,resolved_at,source_event_id)
                VALUES (?,?,?,?,?,?)
                """, f.milestoneId().toString(), f.contractId().toString(), f.flaggedByUserId().toString(),
                f.resolutionFavors().name(), LocalDateTime.ofInstant(f.resolvedAt(), ZoneOffset.UTC), f.sourceEventId().toString()),
                () -> {
                    var rows = jdbc.query("""
                            SELECT * FROM dispute_outcome_fact WHERE milestone_id = ? OR source_event_id = ?
                            """, (rs, n) -> dispute(rs), f.milestoneId().toString(), f.sourceEventId().toString());
                    return rows.size() == 1 && rows.getFirst().sameContent(f);
                });
    }
    private AppendResult insertOrCompare(Runnable insert, Supplier<Boolean> identical) {
        try {
            transaction.executeWithoutResult(status -> insert.run());
            return AppendResult.INSERTED;
        } catch (DuplicateKeyException duplicate) {
            if (Boolean.TRUE.equals(transaction.execute(status -> identical.get()))) return AppendResult.DUPLICATE;
            throw new ImmutableFactConflictException();
        }
    }
    private static CompletedContractFact completed(ResultSet rs) throws SQLException {
        return new CompletedContractFact(id(rs, "contract_id"), id(rs, "buyer_id"), id(rs, "seller_id"),
                rs.getObject("settled_at", LocalDateTime.class).toInstant(ZoneOffset.UTC), id(rs, "source_event_id"));
    }
    private static DisputeOutcomeFact dispute(ResultSet rs) throws SQLException {
        return new DisputeOutcomeFact(id(rs, "milestone_id"), id(rs, "contract_id"), id(rs, "flagged_by_user_id"),
                ResolutionFavors.valueOf(rs.getString("resolution_favors")),
                rs.getObject("resolved_at", LocalDateTime.class).toInstant(ZoneOffset.UTC), id(rs, "source_event_id"));
    }
    private static UUID id(ResultSet rs, String field) throws SQLException { return UUID.fromString(rs.getString(field)); }
}
