package com.agricontract.inspection.application.service;

import com.agricontract.inspection.application.port.InspectionAssignmentRepository;
import com.agricontract.inspection.domain.model.InspectionAssignment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class InspectionAuthorization {
    private final InspectorEligibilityGate eligibility;
    private final InspectionAssignmentRepository assignments;

    public InspectionAuthorization(InspectorEligibilityGate eligibility, InspectionAssignmentRepository assignments) {
        this.eligibility = eligibility;
        this.assignments = assignments;
    }

    public InspectionAssignment requireCommission(String commissionId) {
        String actorId = eligibility.requireEligibleInspector();
        return requireOwner(assignments.findByCommissionId(commissionId), actorId);
    }

    /** Resolves the existing frozen report route without adding commissionId to its DTO. */
    public InspectionAssignment requireMilestone(String contractId, String milestoneId) {
        String actorId = eligibility.requireEligibleInspector();
        return requireOwner(assignments.findActive(contractId, milestoneId), actorId);
    }

    private InspectionAssignment requireOwner(Optional<InspectionAssignment> assignment, String actorId) {
        return assignment.filter(value -> value.isAssignedTo(actorId))
                .orElseThrow(() -> new AccessDeniedException("No authorized inspection assignment"));
    }
}
