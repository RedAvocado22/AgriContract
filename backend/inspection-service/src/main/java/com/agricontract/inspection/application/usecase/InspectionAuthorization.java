package com.agricontract.inspection.application.usecase;

import com.agricontract.inspection.application.exception.InspectionAssignmentAccessDeniedException;
import com.agricontract.inspection.application.port.TransactionRunner;
import com.agricontract.inspection.domain.model.InspectionAssignment;
import com.agricontract.inspection.domain.repository.InspectionAssignmentRepository;

import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class InspectionAuthorization {
    private final InspectorEligibilityGate eligibility;
    private final InspectionAssignmentRepository assignments;
    private final TransactionRunner transactions;

    public InspectionAuthorization(InspectorEligibilityGate eligibility, InspectionAssignmentRepository assignments,
                                   TransactionRunner transactions) {
        this.eligibility = eligibility;
        this.assignments = assignments;
        this.transactions = transactions;
    }

    public InspectionAssignment requireCommission(String commissionId) {
        return transactions.inReadOnlyTransaction(() -> {
            String actorId = eligibility.requireEligibleInspector();
            return requireOwner(assignments.findByCommissionId(commissionId), actorId);
        });
    }

    /** Resolves the existing frozen report route without adding commissionId to its DTO. */
    public InspectionAssignment requireMilestone(String contractId, String milestoneId) {
        return transactions.inReadOnlyTransaction(() -> {
            String actorId = eligibility.requireEligibleInspector();
            return requireOwner(assignments.findActive(contractId, milestoneId), actorId);
        });
    }

    private InspectionAssignment requireOwner(Optional<InspectionAssignment> assignment, String actorId) {
        return assignment.filter(value -> value.isAssignedTo(actorId))
                .orElseThrow(() -> new InspectionAssignmentAccessDeniedException());
    }
}
