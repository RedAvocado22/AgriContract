package com.agricontract.inspection.domain.repository;

import com.agricontract.inspection.domain.model.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Insert/read only; deliberately no save/merge, update, delete or assignment mutation. */
public interface Level15ReportRepository {
    Optional<InspectionAssignment> lockAssignment(String commissionId);
    void insert(ReportIdentity identity, ReportEvidence evidence, Instant createdAt);
    Optional<Level15Report> find(String reportId);
    boolean hasConfirmedReport(String commissionId);
    List<InspectionAssignment> pending(String inspectorId);
}
