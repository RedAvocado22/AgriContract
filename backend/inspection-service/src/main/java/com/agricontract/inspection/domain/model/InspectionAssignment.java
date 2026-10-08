package com.agricontract.inspection.domain.model;

import java.time.Instant;

/** Read model of a persisted Level 1.5 commission assignment, not a creation command. */
public record InspectionAssignment(String commissionId, String contractId, String milestoneId,
                                   String inspectorId, Instant assignedAt, Instant endedAt,
                                   String assignedByService, String initiatedByActorId) {
    public boolean isAssignedTo(String actorId) {
        return endedAt == null && inspectorId.equals(actorId);
    }
}
