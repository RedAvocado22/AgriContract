package com.agricontract.inspection.domain.model;

import java.util.UUID;

/** Server allocation before hashing: reportHash must bind this exact identity. No client ID input. */
public final class ReportIdentity {
    private final String reportId = UUID.randomUUID().toString();
    private final InspectionAssignment assignment;

    private ReportIdentity(InspectionAssignment assignment) { this.assignment = assignment; }

    public static ReportIdentity allocate(InspectionAssignment assignment) {
        return new ReportIdentity(java.util.Objects.requireNonNull(assignment));
    }

    public String reportId() { return reportId; }
    public InspectionAssignment assignment() { return assignment; }
}
