package com.agricontract.bank.infrastructure.persistence;

import com.agricontract.bank.domain.exception.DuplicateLedgerEntryException;
import com.agricontract.bank.domain.model.EntryType;
import com.agricontract.bank.domain.model.FundType;
import com.agricontract.bank.domain.model.LedgerEntry;
import com.agricontract.bank.domain.model.Money;
import com.agricontract.bank.domain.repository.LedgerEntryRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Optional;

// Plain JDBC on purpose: the runtime account only has SELECT, INSERT, so there is no ORM that could try an UPDATE.
@Repository
public class JdbcLedgerEntryRepository implements LedgerEntryRepository {

    private static final String INSERT = """
            INSERT INTO ledger_entry (entry_id, source_event_id, contract_id, milestone_id, user_id, entry_type,
                                      fund_type, remedy_decision_id, remedy_leg_id, amount, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?, CURRENT_TIMESTAMP(6)))
            """;
    private static final String SELECT_BY_SOURCE = """
            SELECT entry_id, source_event_id, contract_id, milestone_id, user_id, entry_type, fund_type,
                   remedy_decision_id, remedy_leg_id, amount, created_at
            FROM ledger_entry WHERE source_event_id = ?
            """;
    private static final String SOURCE_EVENT_KEY = "uk_ledger_source_event";

    private static final RowMapper<LedgerEntry> ROW = (rs, rowNum) -> LedgerEntry.reconstitute(
            rs.getString("entry_id"),
            rs.getString("source_event_id"),
            rs.getString("contract_id"),
            rs.getString("milestone_id"),
            rs.getString("user_id"),
            EntryType.valueOf(rs.getString("entry_type")),
            FundType.valueOf(rs.getString("fund_type")),
            rs.getString("remedy_decision_id"),
            rs.getString("remedy_leg_id"),
            new Money(rs.getBigDecimal("amount")),
            rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbc;

    public JdbcLedgerEntryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(LedgerEntry entry) {
        try {
            jdbc.update(INSERT,
                    entry.getEntryId(),
                    entry.getSourceEventId(),
                    entry.getContractId(),
                    entry.getMilestoneId(),
                    entry.getUserId(),
                    entry.getEntryType().name(),
                    entry.getFundType().name(),
                    entry.getRemedyDecisionId(),
                    entry.getRemedyLegId(),
                    entry.getAmount().amount(),
                    entry.getCreatedAt() == null ? null : Timestamp.from(entry.getCreatedAt()));
        } catch (DuplicateKeyException e) {
            // Only a repeated source event is an idempotent replay; any other unique key is a real conflict.
            if (e.getMessage() != null && e.getMessage().contains(SOURCE_EVENT_KEY)) {
                throw new DuplicateLedgerEntryException(entry.getSourceEventId());
            }
            throw e;
        }
    }

    @Override
    public Optional<LedgerEntry> findBySourceEventId(String sourceEventId) {
        return jdbc.query(SELECT_BY_SOURCE, ROW, sourceEventId).stream().findFirst();
    }
}
