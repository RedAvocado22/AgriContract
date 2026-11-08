package com.agricontract.escrow.infrastructure.persistence;

import com.agricontract.escrow.domain.model.DepositLeg;
import com.agricontract.escrow.domain.model.DepositLegParty;
import com.agricontract.escrow.domain.model.DepositLegStatus;
import com.agricontract.escrow.domain.model.Money;
import com.agricontract.escrow.domain.repository.DepositLegRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcDepositLegRepository implements DepositLegRepository {

    private static final String INSERT = """
            INSERT INTO deposit_leg (leg_id, contract_id, leg, user_id, amount, status, request_event_id,
                                     created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String COLUMNS = """
            leg_id, contract_id, leg, user_id, amount, status, request_event_id, ledger_entry_id, failed_attempts,
            next_attempt_at, last_failure_reason, return_request_event_id, return_ledger_entry_id, created_at,
            updated_at""";
    private static final String SELECT_BY_CONTRACT =
            "SELECT " + COLUMNS + " FROM deposit_leg WHERE contract_id = ? ORDER BY leg";
    private static final String UPDATE_RESULT = """
            UPDATE deposit_leg SET status = ?, ledger_entry_id = ?, failed_attempts = ?, next_attempt_at = ?,
                                   last_failure_reason = ?, return_request_event_id = ?,
                                   return_ledger_entry_id = ?, updated_at = ?
            WHERE leg_id = ?
            """;

    private static final RowMapper<DepositLeg> ROW = (rs, rowNum) -> DepositLeg.reconstitute(
            rs.getString("leg_id"),
            rs.getString("contract_id"),
            DepositLegParty.valueOf(rs.getString("leg")),
            rs.getString("user_id"),
            new Money(rs.getBigDecimal("amount")),
            DepositLegStatus.valueOf(rs.getString("status")),
            rs.getString("request_event_id"),
            rs.getString("ledger_entry_id"),
            rs.getInt("failed_attempts"),
            Optional.ofNullable(rs.getTimestamp("next_attempt_at")).map(Timestamp::toInstant).orElse(null),
            rs.getString("last_failure_reason"),
            rs.getString("return_request_event_id"),
            rs.getString("return_ledger_entry_id"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbc;

    public JdbcDepositLegRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(DepositLeg leg) {
        jdbc.update(INSERT,
                leg.getLegId(),
                leg.getContractId(),
                leg.getParty().name(),
                leg.getUserId(),
                leg.getAmount().amount(),
                leg.getStatus().name(),
                leg.getRequestEventId(),
                Timestamp.from(leg.getCreatedAt()),
                Timestamp.from(leg.getUpdatedAt()));
    }

    @Override
    public List<DepositLeg> findByContractId(String contractId) {
        return jdbc.query(SELECT_BY_CONTRACT, ROW, contractId);
    }

    /**
     * Every leg of the contract, row-locked until the caller's transaction ends. Two results for one contract are
     * then handled one after the other, so the one that completes the set always sees the other (D39).
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<DepositLeg> findByContractIdForUpdate(String contractId) {
        return jdbc.query(SELECT_BY_CONTRACT + " FOR UPDATE", ROW, contractId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void update(DepositLeg leg) {
        int updated = jdbc.update(UPDATE_RESULT,
                leg.getStatus().name(),
                leg.getLedgerEntryId(),
                leg.getFailedAttempts(),
                leg.getNextAttemptAt() == null ? null : Timestamp.from(leg.getNextAttemptAt()),
                leg.getLastFailureReason(),
                leg.getReturnRequestEventId(),
                leg.getReturnLedgerEntryId(),
                Timestamp.from(leg.getUpdatedAt()),
                leg.getLegId());
        if (updated != 1) {
            throw new IllegalStateException("No deposit leg " + leg.getLegId());
        }
    }

    @Override
    public List<String> findContractsWithRetryDue(Instant now, int limit) {
        return jdbc.queryForList("SELECT DISTINCT contract_id FROM deposit_leg"
                        + " WHERE status IN ('FAILED', 'RETURN_FAILED') AND next_attempt_at <= ?"
                        + " ORDER BY contract_id LIMIT ?",
                String.class, Timestamp.from(now), limit);
    }
}
