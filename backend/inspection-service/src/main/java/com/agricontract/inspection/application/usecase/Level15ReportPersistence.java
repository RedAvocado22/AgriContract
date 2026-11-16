package com.agricontract.inspection.application.usecase;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;

import com.agricontract.inspection.application.exception.InspectionAssignmentAccessDeniedException;
import com.agricontract.inspection.application.port.TransactionRunner;
import com.agricontract.inspection.domain.model.InspectionAssignment;
import com.agricontract.inspection.domain.model.Level15Report;
import com.agricontract.inspection.domain.model.ReportEvidence;
import com.agricontract.inspection.domain.model.ReportIdentity;
import com.agricontract.inspection.domain.repository.Level15ReportRepository;

/** Internal capabilities only. No public submission/confirmation or commissioning transport.
 * A future trusted confirmation caller must verify file/result/report hashes and session freshness,
 * persist the signature, close the assignment through #160, and enqueue the confirmed event in the
 * SAME transaction, acquiring #160's milestone lock BEFORE this boundary's assignment row lock.
 * This class supplies only report persistence, not that complete workflow.
 */
@Service
public class Level15ReportPersistence {
    private final InspectionAuthorization authorization;
    private final InspectorEligibilityGate eligibility;
    private final Level15ReportRepository reports;
    private final Clock clock;
    private final TransactionRunner transactions;

    public Level15ReportPersistence(InspectionAuthorization authorization, InspectorEligibilityGate eligibility,
                                    Level15ReportRepository reports, Clock clock, TransactionRunner transactions) {
        this.authorization = authorization;
        this.eligibility = eligibility;
        this.reports = reports;
        this.clock = clock;
        this.transactions = transactions;
    }

    public ReportIdentity allocateIdentity(String contractId, String milestoneId) {
        return transactions.inReadOnlyTransaction(() -> ReportIdentity.allocate(authorization.requireMilestone(contractId, milestoneId)));
    }

    public void persist(ReportIdentity identity, ReportEvidence evidence) {
        transactions.inExistingTransaction(() -> {
            String actor = eligibility.requireEligibleInspector();
            var expected = identity.assignment();
            // Current locking read rechecks ownership/closure, including after allocation in an earlier transaction.
            var current = reports.lockAssignment(expected.commissionId())
                    .orElseThrow(InspectionAssignmentAccessDeniedException::new);
            if (!current.isAssignedTo(actor) || !current.contractId().equals(expected.contractId())
                    || !current.milestoneId().equals(expected.milestoneId())
                    || !current.inspectorId().equals(expected.inspectorId())) {
                throw new InspectionAssignmentAccessDeniedException();
            }
            if (evidence.confirmedAt().isBefore(current.assignedAt()) || evidence.confirmedAt().isAfter(clock.instant())) {
                throw new IllegalArgumentException("Confirmation timestamp is outside the assignment lifetime");
            }
            if (reports.hasConfirmedReport(current.commissionId())) {
                throw new IllegalStateException("Assignment already has a confirmed report");
            }
            reports.insert(identity, evidence, clock.instant().truncatedTo(ChronoUnit.MICROS));
        });
    }

    public List<InspectionAssignment> pending() {
        return transactions.inReadOnlyTransaction(() -> reports.pending(eligibility.requireEligibleInspector()));
    }

    public Level15Report load(String reportId) {
        return transactions.inReadOnlyTransaction(() -> {
            String actor = eligibility.requireEligibleInspector();
            return reports.find(reportId).filter(report -> report.inspectorId().equals(actor))
                    .orElseThrow(InspectionAssignmentAccessDeniedException::new);
        });
    }
}
