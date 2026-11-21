package com.agricontract.contract.infrastructure.persistence.milestone;

import com.agricontract.contract.domain.model.milestone.FundingStatus;
import com.agricontract.contract.domain.model.milestone.Milestone;
import com.agricontract.contract.domain.model.milestone.MilestoneStatus;
import com.agricontract.contract.domain.model.terms.VndAmount;
import com.agricontract.contract.domain.repository.MilestoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/** milestone (V16). Inserted once per contract on activation; later changes update one row under its lock. */
@Repository
@RequiredArgsConstructor
public class JdbcMilestoneRepository implements MilestoneRepository {

    private static final RowMapper<Milestone> ROW = (rs, n) -> Milestone.reconstitute(
            rs.getString("milestone_id"),
            rs.getString("contract_id"),
            rs.getInt("milestone_index"),
            rs.getBigDecimal("committed_quantity").stripTrailingZeros(),
            rs.getObject("expected_delivery_date", LocalDate.class),
            rs.getInt("grace_days"),
            rs.getObject("funding_scheduled_date", LocalDate.class),
            MilestoneStatus.valueOf(rs.getString("status")),
            FundingStatus.valueOf(rs.getString("funding_status")),
            money(rs.getBigDecimal("effective_unit_price")),
            money(rs.getBigDecimal("batch_amount")),
            instant(rs.getTimestamp("funding_requested_at")),
            instant(rs.getTimestamp("funding_locked_at")),
            rs.getInt("funding_delay_business_days"),
            rs.getString("last_funding_failure_reason"));

    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void insertAll(List<Milestone> milestones) {
        Timestamp now = Timestamp.from(Instant.now(clock).truncatedTo(ChronoUnit.MICROS));
        for (Milestone m : milestones) {
            jdbc.update("INSERT INTO milestone (milestone_id, contract_id, milestone_index, committed_quantity,"
                            + " expected_delivery_date, grace_days, funding_scheduled_date, status, funding_status,"
                            + " effective_unit_price, batch_amount, funding_requested_at, funding_locked_at,"
                            + " funding_delay_business_days, last_funding_failure_reason, created_at)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    m.milestoneId(), m.contractId(), m.milestoneIndex(), m.committedQuantity(),
                    m.expectedDeliveryDate(), m.graceDays(), m.fundingScheduledDate(),
                    m.status().name(), m.fundingStatus().name(), value(m.effectiveUnitPrice()),
                    value(m.batchAmount()), timestamp(m.fundingRequestedAt()), timestamp(m.fundingLockedAt()),
                    m.fundingDelayBusinessDays(), m.lastFundingFailureReason(), now);
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void update(Milestone m) {
        int updated = jdbc.update("UPDATE milestone SET status = ?, funding_status = ?, effective_unit_price = ?,"
                        + " batch_amount = ?, funding_requested_at = ?, funding_locked_at = ?,"
                        + " funding_delay_business_days = ?, last_funding_failure_reason = ? WHERE milestone_id = ?",
                m.status().name(), m.fundingStatus().name(), value(m.effectiveUnitPrice()), value(m.batchAmount()),
                timestamp(m.fundingRequestedAt()), timestamp(m.fundingLockedAt()), m.fundingDelayBusinessDays(),
                m.lastFundingFailureReason(), m.milestoneId());
        if (updated != 1) {
            throw new IllegalStateException("Milestone " + m.milestoneId() + " does not exist");
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Milestone> findByIdForUpdate(String milestoneId) {
        return jdbc.query("SELECT * FROM milestone WHERE milestone_id = ? FOR UPDATE", ROW, milestoneId).stream()
                .findFirst();
    }

    @Override
    public List<Milestone> findByContractId(String contractId) {
        return jdbc.query("SELECT * FROM milestone WHERE contract_id = ? ORDER BY milestone_index", ROW, contractId);
    }

    @Override
    public Optional<String> findContractIdOf(String milestoneId) {
        return jdbc.queryForList("SELECT contract_id FROM milestone WHERE milestone_id = ?", String.class, milestoneId)
                .stream().findFirst();
    }

    @Override
    public List<String> findIdsDueForFunding(LocalDate today, int limit) {
        return jdbc.queryForList("SELECT m.milestone_id FROM milestone m JOIN contracts c ON c.contract_id ="
                        + " m.contract_id WHERE m.funding_status = 'NOT_REQUESTED' AND m.funding_scheduled_date <= ?"
                        + " AND c.status = 'ACTIVE' ORDER BY m.funding_scheduled_date, m.contract_id,"
                        + " m.milestone_index LIMIT ?",
                String.class, today, limit);
    }

    private static VndAmount money(BigDecimal value) {
        return value == null ? null : new VndAmount(value);
    }

    private static BigDecimal value(VndAmount amount) {
        return amount == null ? null : amount.value();
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
