package com.agricontract.inspection.domain.model;

import java.time.Instant;
import java.util.Objects;

public record TimedAssignment(String commissionId, String contractId, String milestoneId,
                              String inspectorId, Instant assignedAt, Instant endedAt,
                              int reportDueBusinessDays, Instant reportDueAt, String initiatedByActorId,
                              String assignedByService) {
    public TimedAssignment(String commissionId, String contractId, String milestoneId,
                           String inspectorId, Instant assignedAt, Instant endedAt,
                           int reportDueBusinessDays, Instant reportDueAt, String initiatedByActorId) {
        this(commissionId, contractId, milestoneId, inspectorId, assignedAt, endedAt,
                reportDueBusinessDays, reportDueAt, initiatedByActorId, "contract-service");
    }
    public TimedAssignment {
        Objects.requireNonNull(reportDueAt, "reportDueAt");
    }
}
