package com.agricontract.contract.infrastructure.persistence.activation;

import com.agricontract.contract.domain.model.activation.ActivationCancelRequest;
import com.agricontract.contract.domain.model.consent.SignerRole;
import com.agricontract.contract.domain.repository.ActivationCancelRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** activation_cancel_request (V14). The unique key on the generated standing_role allows one standing row per party. */
@Repository
@RequiredArgsConstructor
public class JdbcActivationCancelRequestRepository implements ActivationCancelRequestRepository {

    private static final RowMapper<ActivationCancelRequest> ROW = (rs, n) -> new ActivationCancelRequest(
            UUID.fromString(rs.getString("request_id")),
            rs.getString("contract_id"),
            SignerRole.valueOf(rs.getString("party_role")),
            rs.getString("user_id"),
            UUID.fromString(rs.getString("otp_id")),
            rs.getTimestamp("requested_at").toInstant(),
            Optional.ofNullable(rs.getTimestamp("withdrawn_at")).map(Timestamp::toInstant).orElse(null));

    private final JdbcTemplate jdbc;

    @Override
    public List<ActivationCancelRequest> findStanding(String contractId) {
        return jdbc.query("SELECT * FROM activation_cancel_request WHERE contract_id = ? AND withdrawn_at IS NULL"
                + " ORDER BY requested_at", ROW, contractId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(ActivationCancelRequest r) {
        jdbc.update("INSERT INTO activation_cancel_request (request_id, contract_id, party_role, user_id, otp_id,"
                        + " requested_at, withdrawn_at) VALUES (?, ?, ?, ?, ?, ?, NULL)",
                r.requestId().toString(), r.contractId(), r.partyRole().name(), r.userId(), r.otpId().toString(),
                Timestamp.from(r.requestedAt()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void withdraw(UUID requestId, Instant at) {
        int updated = jdbc.update("UPDATE activation_cancel_request SET withdrawn_at = ? WHERE request_id = ?"
                + " AND withdrawn_at IS NULL", Timestamp.from(at), requestId.toString());
        if (updated != 1) {
            throw new IllegalStateException("No standing cancel request " + requestId);
        }
    }

    @Override
    public List<String> findContractsWithOneSidedCancelDue(Instant now, int limit) {
        return jdbc.queryForList("SELECT DISTINCT r.contract_id FROM activation_recovery r"
                        + " JOIN contracts c ON c.contract_id = r.contract_id"
                        + " JOIN activation_cancel_request q ON q.contract_id = r.contract_id AND q.withdrawn_at IS NULL"
                        + " WHERE c.status = 'SIGNED' AND r.one_sided_cancel_allowed_from <= ?"
                        + " ORDER BY r.contract_id LIMIT ?",
                String.class, Timestamp.from(now), limit);
    }
}
