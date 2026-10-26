package com.agricontract.bank.infrastructure.persistence;

import com.agricontract.bank.domain.model.LedgerEntry;
import com.agricontract.bank.domain.repository.LedgerEntryRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;

// Plain JDBC on purpose: the runtime account only has SELECT, INSERT, so there is no ORM that could try an UPDATE.
// A duplicate source_event_id / remedy_leg_id surfaces as DataIntegrityViolationException for the caller to treat
// as an idempotent replay.
@Repository
public class JdbcLedgerEntryRepository implements LedgerEntryRepository {

    private static final String INSERT = """
            INSERT INTO ledger_entry (entry_id, source_event_id, contract_id, milestone_id, user_id, entry_type,
                                      fund_type, remedy_decision_id, remedy_leg_id, amount, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?, CURRENT_TIMESTAMP(6)))
            """;

    private final JdbcTemplate jdbc;

    public JdbcLedgerEntryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(LedgerEntry entry) {
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
    }
}
