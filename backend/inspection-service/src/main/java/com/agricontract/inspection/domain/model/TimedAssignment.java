package com.agricontract.inspection.domain.model;

import java.time.Instant;

public record TimedAssignment(String commissionId, String contractId, String milestoneId,
                              String inspectorId, Instant assignedAt, Instant endedAt,
                              Integer reportDueBusinessDays, Instant reportDueAt, String initiatedByActorId) { }
