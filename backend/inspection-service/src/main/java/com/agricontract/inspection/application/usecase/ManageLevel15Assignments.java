package com.agricontract.inspection.application.usecase;

import com.agricontract.eventing.EventEnvelope;
import com.agricontract.eventing.inbox.ProcessedEventStore;
import com.agricontract.inspection.application.port.*;
import com.agricontract.inspection.domain.model.*;
import com.agricontract.inspection.domain.repository.AssignmentLifecycleRepository;
import com.agricontract.inspection.application.service.VietnamBusinessDays;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.random.RandomGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class ManageLevel15Assignments {
    private final AssignmentLifecycleRepository assignments;
    private final InspectorEligibilityPort inspectors;
    private final ProcessedEventStore processed;
    private final VietnamBusinessDays calendar;
    private final RandomGenerator random;
    private final AssignmentAlerts alerts;
    private final Clock clock;

    @Transactional
    public void requested(EventEnvelope envelope, AssignmentRequest request) {
        if (!processed.markProcessed("inspection.level1_5_requested", envelope)) return;
        assignments.lock(request.contractId(), request.milestoneId());
        if (assignments.active(request.contractId(), request.milestoneId()).isPresent()) return;
        pick(request.contractId(), request.milestoneId(), request.reportDueBusinessDays(),
                request.initiatedByActorId(), envelope.eventId().toString(), false, Set.of());
    }

    @Transactional
    public void timeout(String commissionId) {
        var initial = assignments.find(commissionId).orElseThrow();
        assignments.lock(initial.contractId(), initial.milestoneId());
        var current = assignments.active(initial.contractId(), initial.milestoneId());
        Instant now = now();
        if (current.isEmpty() || !current.get().commissionId().equals(commissionId)) return;
        var old = current.get();
        if (old.reportDueAt().isAfter(now)) return;
        assignments.end(commissionId, old.reportDueAt());
        pick(old.contractId(), old.milestoneId(), old.reportDueBusinessDays(), old.initiatedByActorId(),
                commissionId, true, assignments.previousInspectors(old.contractId(), old.milestoneId()));
    }

    private void pick(String contract, String milestone, int days, String actor, String source,
                      boolean replacement, Set<String> excluded) {
        // The complete authoritative pool is fetched for each attempt. No local registry or cached eligibility.
        var candidates = inspectors.loadEligibleInspectors().stream().distinct()
                .filter(id -> !excluded.contains(id)).toList();
        if (candidates.isEmpty()) {
            alerts.emptyPool(contract, milestone, source, replacement);
            return;
        }
        String inspector = candidates.get(random.nextInt(candidates.size()));
        Instant assigned = now();
        assignments.insert(new TimedAssignment(UUID.randomUUID().toString(), contract, milestone,
                inspector, assigned, null, days, calendar.deadline(assigned, days), actor));
    }

    /** #129 must call this inside its report confirmation transaction, after its authorization checks.
     * A stale/foreign assignment fails the whole confirmation transaction. No independent commit is allowed.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void closeConfirmed(String contract, String milestone, String commission, String inspector, Instant confirmedAt) {
        assignments.lock(contract, milestone);
        var active = assignments.active(contract, milestone).orElseThrow(() -> new IllegalStateException("No active assignment"));
        if (!active.commissionId().equals(commission) || !active.inspectorId().equals(inspector)
                || confirmedAt == null || confirmedAt.isBefore(active.assignedAt()) || confirmedAt.isAfter(clock.instant())
                || !confirmedAt.isBefore(active.reportDueAt()))
            throw new IllegalStateException("Report does not match active assignment");
        if (!assignments.end(commission, confirmedAt))
            throw new IllegalStateException("Assignment already ended");
    }

    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }
}
