package com.agricontract.contract.application.usecase;

import com.agricontract.calendar.VietnamBusinessDays;
import com.agricontract.contract.application.port.TransactionRunner;
import com.agricontract.contract.application.service.MilestoneFunding;
import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.milestone.Milestone;
import com.agricontract.contract.domain.model.vo.ContractId;
import com.agricontract.contract.domain.repository.ContractRepository;
import com.agricontract.contract.domain.repository.MilestoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * D64: asks escrow to lock each milestone's batch on its fundingScheduledDate. Each milestone is re-checked under the
 * contract and milestone row locks in its own transaction, so a request already made (by the activation, or by another
 * poll) is not made twice, and one failing milestone does not stop the next. Milestones of a contract that is no
 * longer ACTIVE are never returned by the query.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequestDueMilestoneFundingUseCase {
    private static final int BATCH = 50;

    private final MilestoneRepository milestones;
    private final ContractRepository contracts;
    private final MilestoneFunding funding;
    private final TransactionRunner transactions;
    private final Clock clock;

    /** Returns how many funding requests were written. */
    public int execute() {
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        LocalDate today = now.atZone(VietnamBusinessDays.ICT).toLocalDate();
        List<String> due = milestones.findIdsDueForFunding(today, BATCH);
        int requested = 0;
        for (String milestoneId : due) {
            try {
                if (Boolean.TRUE.equals(transactions.inTransaction(() -> requestOne(milestoneId, today, now)))) {
                    requested++;
                }
            } catch (RuntimeException e) {
                log.error("event=milestone_funding_request_failed milestoneId={} reason={}", milestoneId,
                        e.getMessage(), e);
            }
        }
        return requested;
    }

    private boolean requestOne(String milestoneId, LocalDate today, Instant now) {
        String contractId = milestones.findContractIdOf(milestoneId).orElse(null);
        if (contractId == null) {
            return false;
        }
        // Contract lock first, as every contract-service write does, then the milestone.
        Contract contract = contracts.findByIdForUpdate(new ContractId(contractId)).orElse(null);
        Milestone milestone = milestones.findByIdForUpdate(milestoneId).orElse(null);
        if (contract == null || milestone == null || !milestone.isFundingDue(today)) {
            return false;
        }
        funding.request(contract, milestone, now, contractId, null);
        return true;
    }
}
