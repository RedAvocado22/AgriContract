package com.agricontract.inspection.domain.model;

import java.time.Instant;
import java.util.Objects;

public record TimedAssignment(String commissionId, String contractId, String milestoneId,
                              String inspectorId, Instant assignedAt, Instant endedAt,
                              int reportDueBusinessDays, Instant reportDueAt, String initiatedByActorId) {
    public TimedAssignment {
        Objects.requireNonNull(reportDueAt, "reportDueAt");
    }
}
