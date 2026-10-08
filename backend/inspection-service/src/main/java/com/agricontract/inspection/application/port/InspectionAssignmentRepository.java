package com.agricontract.inspection.application.port;

import com.agricontract.inspection.domain.model.InspectionAssignment;
import java.util.Optional;

/** Read-only until assignment creation authority and transport have been designed. */
public interface InspectionAssignmentRepository {
    Optional<InspectionAssignment> findByCommissionId(String commissionId);
    Optional<InspectionAssignment> findActive(String contractId, String milestoneId);
}
