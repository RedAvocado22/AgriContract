package com.agricontract.contract.infrastructure.persistence.activation;

import com.agricontract.contract.domain.model.activation.ActivationRecovery;
import com.agricontract.contract.domain.repository.ActivationRecoveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.Optional;

/** activation_recovery (V13). One row per contract; deposit_lock_failed_at and the window are never updated. */
@Repository
@RequiredArgsConstructor
public class JdbcActivationRecoveryRepository implements ActivationRecoveryRepository {

    private static final RowMapper<ActivationRecovery> ROW = (rs, n) -> ActivationRecovery.reconstitute(
            rs.getString("contract_id"),
            rs.getTimestamp("deposit_lock_failed_at").toInstant(),
            rs.getTimestamp("one_sided_cancel_allowed_from").toInstant(),
            ActivationRecovery.FailedLeg.valueOf(rs.getString("last_failed_leg")),
            rs.getString("last_failure_reason"),
            rs.getTimestamp("last_failed_at").toInstant(),
            rs.getInt("failure_reports"));

    private final JdbcTemplate jdbc;

    @Override
    public Optional<ActivationRecovery> findByContractId(String contractId) {
        return jdbc.query("SELECT * FROM activation_recovery WHERE contract_id = ?", ROW, contractId).stream()
                .findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(ActivationRecovery r) {
        int updated = jdbc.update("UPDATE activation_recovery SET last_failed_leg = ?, last_failure_reason = ?,"
                        + " last_failed_at = ?, failure_reports = ? WHERE contract_id = ?",
                r.lastFailedLeg().name(), r.lastFailureReason(), Timestamp.from(r.lastFailedAt()),
                r.failureReports(), r.contractId());
        if (updated == 0) {
            jdbc.update("INSERT INTO activation_recovery (contract_id, deposit_lock_failed_at,"
                            + " one_sided_cancel_allowed_from, last_failed_leg, last_failure_reason, last_failed_at,"
                            + " failure_reports) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    r.contractId(), Timestamp.from(r.depositLockFailedAt()),
                    Timestamp.from(r.oneSidedCancelAllowedFrom()), r.lastFailedLeg().name(), r.lastFailureReason(),
                    Timestamp.from(r.lastFailedAt()), r.failureReports());
        }
    }
}
