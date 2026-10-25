package com.agricontract.inspection.domain.repository;

import com.agricontract.inspection.domain.model.TimedAssignment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface AssignmentLifecycleRepository {
    void lock(String contractId, String milestoneId);
    Optional<TimedAssignment> active(String contractId, String milestoneId);
    Optional<TimedAssignment> find(String commissionId);
    Set<String> previousInspectors(String contractId, String milestoneId);
    void insert(TimedAssignment assignment);
    boolean end(String commissionId, Instant endedAt);
    List<TimedAssignment> overdue(Instant now, int limit);
}
