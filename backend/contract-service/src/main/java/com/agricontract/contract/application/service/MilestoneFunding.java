package com.agricontract.contract.application.service;

import com.agricontract.calendar.VietnamBusinessDays;
import com.agricontract.contract.application.port.ContractEventPublisher;
import com.agricontract.contract.application.port.TransactionRunner;
import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.milestone.Milestone;
import com.agricontract.contract.domain.model.terms.ContractTermsSnapshot;
import com.agricontract.contract.domain.model.terms.MilestoneTerm;
import com.agricontract.contract.domain.model.vo.ContractStatus;
import com.agricontract.contract.domain.repository.MilestoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Milestone creation and funding requests (D63, D64), shared by the activation and the daily schedule. Every method
 * runs inside the caller's transaction, under the contract row lock, so milestone rows and milestone.funding_requested
 * commit with the change that caused them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MilestoneFunding {
    private final MilestoneRepository milestones;
    private final ContractEventPublisher events;
    private final VietnamBusinessDays calendar;
    private final TransactionRunner transactions;

    /**
     * The contract just became ACTIVE: one CREATED milestone per signed term, funding scheduled
     * fundingLeadBusinessDays before its delivery date. A milestone whose date has already come is requested now,
     * in this transaction, instead of waiting for the next poll.
     */
    public List<Milestone> planOnActivation(Contract contract, Instant activatedAt, String correlationId,
                                            String causationId) {
        transactions.requireTransaction();
        ContractTermsSnapshot terms = contract.getCoffeeTerms().snapshot();
        String contractId = contract.getContractId().value();
        int lead = terms.fundingLeadBusinessDays();
        List<Milestone> planned = terms.negotiated().milestoneSchedule().stream()
                .sorted(Comparator.comparing(MilestoneTerm::milestoneIndex))
                .map(term -> Milestone.plan(UUID.randomUUID().toString(), contractId, term,
                        calendar.businessDaysBefore(term.expectedDeliveryDate(), lead)))
                .toList();
        milestones.insertAll(planned);

        LocalDate today = activatedAt.atZone(VietnamBusinessDays.ICT).toLocalDate();
        for (Milestone milestone : planned) {
            if (milestone.isFundingDue(today)) {
                request(contract, milestone, activatedAt, correlationId, causationId);
            }
        }
        return planned;
    }

    /**
     * Fixes the batch and writes milestone.funding_requested. Refuses a contract that is not ACTIVE: a terminated
     * contract asks for no more money.
     */
    public void request(Contract contract, Milestone milestone, Instant now, String correlationId,
                        String causationId) {
        transactions.requireTransaction();
        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw new IllegalStateException("Funding requested for contract " + contract.getContractId().value()
                    + " in status " + contract.getStatus());
        }
        // Until bounded price adjustment (#237) exists, the effective unit price is the signed agreedPrice.
        milestone.requestFunding(contract.getCoffeeTerms().snapshot().negotiated().agreedPrice(), now);
        milestones.update(milestone);
        events.milestoneFundingRequested(contract, milestone, correlationId, causationId);
        log.info("event=milestone_funding_requested contractId={} milestoneId={} index={} batchAmount={}",
                milestone.contractId(), milestone.milestoneId(), milestone.milestoneIndex(),
                milestone.batchAmount().wire());
    }
}
