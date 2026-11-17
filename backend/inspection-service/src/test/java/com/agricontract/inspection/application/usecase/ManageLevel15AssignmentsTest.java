package com.agricontract.inspection.application.usecase;

import com.agricontract.calendar.VietnamBusinessDays;
import com.agricontract.inspection.application.dto.Level15AssignmentRequestedCommand;
import com.agricontract.inspection.application.port.AssignmentAlerts;
import com.agricontract.inspection.application.port.AssignmentWaitingRepository;
import com.agricontract.inspection.domain.model.WaitingAssignment;
import com.agricontract.inspection.application.port.InspectorEligibilityPort;
import com.agricontract.inspection.domain.model.TimedAssignment;
import com.agricontract.inspection.domain.repository.AssignmentLifecycleRepository;
import com.agricontract.inspection.testsupport.DirectTransactionRunner;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ManageLevel15AssignmentsTest {
    private static final Instant NOW = Instant.parse("2026-10-23T03:00:00Z");
    private final AssignmentLifecycleRepository assignments = mock(AssignmentLifecycleRepository.class);
    private final InspectorEligibilityPort inspectors = mock(InspectorEligibilityPort.class);
    private final RandomGenerator random = mock(RandomGenerator.class);
    private final AssignmentWaitingRepository waiting = mock(AssignmentWaitingRepository.class);
    private final AssignmentAlerts alerts = mock(AssignmentAlerts.class);
    private final DirectTransactionRunner transactions = spy(new DirectTransactionRunner());
    private final VietnamBusinessDays calendar = VietnamBusinessDays.fromConfiguration("2026-01-01");
    private final ManageLevel15Assignments lifecycle = new ManageLevel15Assignments(
            assignments, inspectors, calendar, random, alerts, Clock.fixed(NOW, ZoneOffset.UTC), transactions, waiting);

    @Test
    void requestCreatesAssignmentWithExistingDeadlineAndProvenance() {
        when(inspectors.loadEligibleInspectors()).thenReturn(List.of("a", "a", "b"));
        when(random.nextInt(2)).thenReturn(1);
        lifecycle.requested(request());
        var inserted = ArgumentCaptor.forClass(TimedAssignment.class);
        verify(assignments).insert(inserted.capture());
        assertThat(inserted.getValue().inspectorId()).isEqualTo("b");
        assertThat(inserted.getValue().contractId()).isEqualTo("contract");
        assertThat(inserted.getValue().milestoneId()).isEqualTo("milestone");
        assertThat(inserted.getValue().initiatedByActorId()).isEqualTo("actor");
        assertThat(inserted.getValue().assignedAt()).isEqualTo(NOW);
        assertThat(inserted.getValue().reportDueAt()).isEqualTo(calendar.deadline(NOW, 1));
        verify(transactions).inTransaction(any(Runnable.class));
    }

    @Test
    void activeAssignmentMakesRequestANoOp() {
        when(assignments.active("contract", "milestone")).thenReturn(Optional.of(assignment(NOW)));
        lifecycle.requested(request());
        verify(assignments, never()).insert(any());
        verifyNoInteractions(inspectors, random, alerts);
    }

    @Test
    void emptyPoolRetainsAlertAndDoesNotCreateAssignment() {
        when(inspectors.loadEligibleInspectors()).thenReturn(List.of());
        lifecycle.requested(request());
        var pending = ArgumentCaptor.forClass(WaitingAssignment.class);
        verify(waiting).insert(pending.capture());
        assertThat(pending.getValue().reason()).isEqualTo(WaitingAssignment.Reason.NO_ELIGIBLE);
        assertThat(pending.getValue().waitingSince()).isEqualTo(NOW);
        verify(alerts).waitingChanged(pending.getValue(), AssignmentAlerts.Stage.WAITING_STARTED);
        verify(assignments, never()).insert(any());
        verifyNoInteractions(random);
    }

    @Test
    void waitingRequestIsNoOpWithoutLookingUpEligibility() {
        when(waiting.exists(new WaitingAssignment.Key("contract", "milestone"))).thenReturn(true);
        lifecycle.requested(request());
        verify(assignments, never()).insert(any());
        verify(waiting, never()).insert(any());
        verifyNoInteractions(inspectors, random, alerts);
    }

    @Test
    void newRequestUsesExistingFirstPickRules() {
        when(inspectors.loadEligibleInspectors()).thenReturn(List.of("a", "b"));
        lifecycle.requested(request());
        var inserted = ArgumentCaptor.forClass(TimedAssignment.class);
        verify(assignments).insert(inserted.capture());
        assertThat(inserted.getValue().inspectorId()).isEqualTo("a");
        verify(random).nextInt(2);
        verify(assignments, never()).previousInspectors(any(), any());
    }

    @Test
    void dependencyFailureRemainsRetryable() {
        var failure = new IllegalStateException("unavailable");
        when(inspectors.loadEligibleInspectors()).thenThrow(failure);
        assertThatThrownBy(() -> lifecycle.requested(request())).isSameAs(failure);
        verify(assignments, never()).insert(any());
        verifyNoInteractions(alerts);
    }

    @Test
    void timeoutEndsOldAssignmentAndExcludesPreviousInspectors() {
        var old = assignment(NOW);
        when(assignments.find("commission")).thenReturn(Optional.of(old));
        when(assignments.active("contract", "milestone")).thenReturn(Optional.of(old));
        when(assignments.end("commission", NOW)).thenReturn(true);
        when(assignments.previousInspectors("contract", "milestone")).thenReturn(Set.of("a"));
        when(inspectors.loadEligibleInspectors()).thenReturn(List.of("a", "b"));
        lifecycle.timeout("commission");
        verify(assignments).end("commission", NOW);
        var inserted = ArgumentCaptor.forClass(TimedAssignment.class);
        verify(assignments).insert(inserted.capture());
        assertThat(inserted.getValue().inspectorId()).isEqualTo("b");
        verify(transactions).inTransaction(any(Runnable.class));
    }

    @Test
    void beforeDeadlineDoesNotEndOrRepick() {
        var old = assignment(NOW.plusSeconds(1));
        when(assignments.find("commission")).thenReturn(Optional.of(old));
        when(assignments.active("contract", "milestone")).thenReturn(Optional.of(old));
        lifecycle.timeout("commission");
        verify(assignments, never()).end(anyString(), any());
        verifyNoInteractions(inspectors, random, alerts);
    }

    @Test
    void confirmationUsesExistingTransactionBoundaryAndPreservesOwnerCheck() {
        when(assignments.active("contract", "milestone")).thenReturn(Optional.of(assignment(NOW.plusSeconds(1))));
        when(assignments.end("commission", NOW)).thenReturn(true);
        lifecycle.closeConfirmed("contract", "milestone", "commission", "a", NOW);
        verify(transactions).inExistingTransaction(any(Runnable.class));
        assertThatThrownBy(() -> lifecycle.closeConfirmed("contract", "milestone", "commission", "b", NOW))
                .isInstanceOf(IllegalStateException.class).hasMessage("Report does not match active assignment");
        verify(assignments, times(1)).end("commission", NOW);
    }

    private Level15AssignmentRequestedCommand request() {
        return new Level15AssignmentRequestedCommand("contract", "milestone", 1, "actor", "event");
    }

    private TimedAssignment assignment(Instant deadline) {
        return new TimedAssignment("commission", "contract", "milestone", "a", NOW.minusSeconds(1),
                null, 1, deadline, "actor");
    }
}
