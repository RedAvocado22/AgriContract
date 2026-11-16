package com.agricontract.inspection.domain.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.agricontract.inspection.domain.model.InspectionAssignment;
import com.agricontract.inspection.domain.model.Level15Report;
import com.agricontract.inspection.domain.model.ReportEvidence;
import com.agricontract.inspection.domain.model.ReportIdentity;

/** Insert/read only; deliberately no save/merge, update, delete or assignment mutation. */
public interface Level15ReportRepository {
    Optional<InspectionAssignment> lockAssignment(String commissionId);
    void insert(ReportIdentity identity, ReportEvidence evidence, Instant createdAt);
    Optional<Level15Report> find(String reportId);
    boolean hasConfirmedReport(String commissionId);
    List<InspectionAssignment> pending(String inspectorId);
}
