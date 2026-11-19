package com.agricontract.inspection.application.usecase;

import com.agricontract.inspection.application.dto.Level15AssignmentRequestedCommand;
import com.agricontract.inspection.application.port.AssignmentAlerts;
import com.agricontract.inspection.application.port.AssignmentWaitingRepository;
import com.agricontract.inspection.application.port.InspectorEligibilityPort;
import com.agricontract.inspection.application.port.TransactionRunner;
import com.agricontract.inspection.domain.model.TimedAssignment;
import com.agricontract.inspection.domain.model.WaitingAssignment;
import com.agricontract.inspection.domain.repository.AssignmentLifecycleRepository;
import com.agricontract.calendar.VietnamBusinessDays;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ManageLevel15Assignments {
    private final AssignmentLifecycleRepository assignments;
    private final InspectorEligibilityPort inspectors;
    private final VietnamBusinessDays calendar;
    private final RandomGenerator random;
    private final AssignmentAlerts alerts;
    private final Clock clock;
    private final TransactionRunner transactions;
    private final AssignmentWaitingRepository waiting;

    public void requested(Level15AssignmentRequestedCommand request) {
        transactions.inReadCommittedTransaction(() -> {
            assignments.lock(request.contractId(), request.milestoneId());
            if (assignments.active(request.contractId(), request.milestoneId()).isPresent()) {
                return;
            }
            pick(request.contractId(), request.milestoneId(), request.reportDueBusinessDays(),
                    request.initiatedByActorId(), "contract-service",
                    WaitingAssignment.Reason.NO_ELIGIBLE, Set.of(), request.correlationId(), request.sourceEventId());
        });
    }

    public void timeout(String commissionId) {
        transactions.inReadCommittedTransaction(() -> {
            var initial = assignments.find(commissionId).orElseThrow();
            assignments.lock(initial.contractId(), initial.milestoneId());
            var current = assignments.active(initial.contractId(), initial.milestoneId());
            Instant now = now();
            if (current.isEmpty() || !current.get().commissionId().equals(commissionId)) {
                return;
            }
            var old = current.get();
            if (old.reportDueAt().isAfter(now)) {
                return;
            }
            if (!assignments.end(commissionId, old.reportDueAt())) {
                throw new IllegalStateException("Assignment already ended");
            }
            pick(old.contractId(), old.milestoneId(), old.reportDueBusinessDays(), old.initiatedByActorId(),
                    old.assignedByService(), WaitingAssignment.Reason.ALL_EXCLUDED,
                    assignments.previousInspectors(old.contractId(), old.milestoneId()), UUID.randomUUID().toString(), null);
        });
    }

    private void pick(String contract, String milestone, int days, String actor, String service,
                      WaitingAssignment.Reason reason, Set<String> excluded, String correlationId, String causationId) {
        Instant attempted = now();
        var pending = new WaitingAssignment(contract, milestone, reason, days, service, actor,
                attempted, attempted, null);
        // Reserve the key under the milestone guard without a missing-row gap lock.
        if (!waiting.tryReserve(pending)) {
            if (reason == WaitingAssignment.Reason.ALL_EXCLUDED) {
                throw new IllegalStateException("Active assignment has a waiting request");
            }
            return;
        }
        var candidates = candidates(excluded);
        if (candidates.isEmpty()) {
            attempted = now();
            LocalDate startDate = attempted.atZone(VietnamBusinessDays.ICT).toLocalDate();
            LocalDate reminder = calendar.isBusinessDay(startDate) ? startDate : null;
            waiting.started(pending.key(), attempted, reminder);
            pending = new WaitingAssignment(contract, milestone, reason, days, service, actor,
                    attempted, attempted, reminder);
            alerts.waitingChanged(pending, AssignmentAlerts.Stage.WAITING_STARTED, correlationId, causationId);
        } else {
            assign(contract, milestone, days, actor, service, candidates);
            waiting.delete(pending.key());
        }
    }

    private List<String> candidates(Set<String> excluded) {
        // The complete authoritative pool is fetched for each attempt. No local registry or cached eligibility.
        return inspectors.loadEligibleInspectors().stream().distinct()
                .filter(id -> !excluded.contains(id)).toList();
    }

    private void assign(String contract, String milestone, int days, String actor, String service,
                        List<String> candidates) {
        String inspector = candidates.get(random.nextInt(candidates.size()));
        Instant assigned = now();
        assignments.insert(new TimedAssignment(UUID.randomUUID().toString(), contract, milestone,
                inspector, assigned, null, days, calendar.deadline(assigned, days), actor, service));
    }

    public void retryWaiting(WaitingAssignment.Key key) {
        transactions.inReadCommittedTransaction(() -> {
            // Same lock order as requested/timeout: milestone guard, then waiting row.
            // Both are nonblocking so another worker can continue with unrelated rows.
            if (!assignments.tryLock(key.contractId(), key.milestoneId())) {
                return;
            }
            var locked = waiting.lockSkippingLocked(key);
            if (locked.isEmpty()) {
                return;
            }
            var pending = locked.get();
            // Preserve the row for investigation if an invariant was already violated.
            if (assignments.active(key.contractId(), key.milestoneId()).isPresent()) {
                throw new IllegalStateException("Waiting request has an active assignment");
            }
            var candidates = candidates(assignments.previousInspectors(key.contractId(), key.milestoneId()));
            if (!candidates.isEmpty()) {
                assign(key.contractId(), key.milestoneId(), pending.reportDueBusinessDays(),
                        pending.initiatedByActorId(), pending.assignedByService(), candidates);
                waiting.delete(key);
                alerts.waitingChanged(pending, AssignmentAlerts.Stage.ASSIGNED, UUID.randomUUID().toString(), null);
                return;
            }
            Instant attempted = now();
            LocalDate today = attempted.atZone(VietnamBusinessDays.ICT).toLocalDate();
            LocalDate reminder = pending.lastReminderOn();
            if (calendar.isBusinessDay(today) && !today.equals(reminder)) {
                alerts.waitingChanged(pending, AssignmentAlerts.Stage.STILL_WAITING, UUID.randomUUID().toString(), null);
                reminder = today;
            }
            waiting.attempted(key, attempted, reminder);
        });
    }

    /** #129 must call this inside its report confirmation transaction, after its authorization checks.
     * A stale/foreign assignment fails the whole confirmation transaction. No independent commit is allowed.
     */
    public void closeConfirmed(String contract, String milestone, String commission, String inspector, Instant confirmedAt) {
        transactions.inExistingTransaction(() -> {
            assignments.lock(contract, milestone);
            var active = assignments.active(contract, milestone).orElseThrow(() -> new IllegalStateException("No active assignment"));
            if (!active.commissionId().equals(commission) || !active.inspectorId().equals(inspector)
                    || confirmedAt == null || confirmedAt.isBefore(active.assignedAt()) || confirmedAt.isAfter(clock.instant())
                    || !confirmedAt.isBefore(active.reportDueAt())) {
                throw new IllegalStateException("Report does not match active assignment");
            }
            if (!assignments.end(commission, confirmedAt)) {
                throw new IllegalStateException("Assignment already ended");
            }
        });
    }

    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }
}
