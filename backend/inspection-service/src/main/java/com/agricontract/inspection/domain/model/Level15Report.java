package com.agricontract.inspection.domain.model;

import java.time.Instant;

/** Immutable persisted CONFIRMED snapshot. No Level 1.5 draft/PENDING_REVIEW state. */
public record Level15Report(String reportId, String commissionId, String contractId,
                            String milestoneId, String inspectorId, ReportEvidence evidence,
                            Instant createdAt) { }
