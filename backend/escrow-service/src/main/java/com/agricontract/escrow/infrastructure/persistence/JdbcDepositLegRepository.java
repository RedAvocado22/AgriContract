package com.agricontract.escrow.infrastructure.persistence;

import com.agricontract.escrow.domain.model.DepositLeg;
import com.agricontract.escrow.domain.model.DepositLegParty;
import com.agricontract.escrow.domain.model.DepositLegStatus;
import com.agricontract.escrow.domain.model.Money;
import com.agricontract.escrow.domain.repository.DepositLegRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public class JdbcDepositLegRepository implements DepositLegRepository {

    private static final String INSERT = """
            INSERT INTO deposit_leg (leg_id, contract_id, leg, user_id, amount, status, request_event_id,
                                     created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT_BY_CONTRACT = """
            SELECT leg_id, contract_id, leg, user_id, amount, status, request_event_id, created_at, updated_at
            FROM deposit_leg WHERE contract_id = ? ORDER BY leg
            """;

    private static final RowMapper<DepositLeg> ROW = (rs, rowNum) -> DepositLeg.reconstitute(
            rs.getString("leg_id"),
            rs.getString("contract_id"),
            DepositLegParty.valueOf(rs.getString("leg")),
            rs.getString("user_id"),
            new Money(rs.getBigDecimal("amount")),
            DepositLegStatus.valueOf(rs.getString("status")),
            rs.getString("request_event_id"),
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
}
