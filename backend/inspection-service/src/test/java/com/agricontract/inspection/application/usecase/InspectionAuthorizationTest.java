package com.agricontract.inspection.application.usecase;

import com.agricontract.inspection.application.exception.EligibilityUnavailableException;
import com.agricontract.inspection.application.exception.InspectionAssignmentAccessDeniedException;
import com.agricontract.inspection.application.exception.InspectorNotEligibleException;
import com.agricontract.inspection.domain.model.InspectionAssignment;
import com.agricontract.inspection.domain.repository.InspectionAssignmentRepository;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class InspectionAuthorizationTest {
    private final InspectorEligibilityGate eligibility = mock(InspectorEligibilityGate.class);
    private final InspectionAssignmentRepository assignments = mock(InspectionAssignmentRepository.class);
    private final InspectionAuthorization authorization = new InspectionAuthorization(eligibility, assignments);

    @Test
    void eligibleAssignedInspectorCanAccessCommission() {
        when(eligibility.requireEligibleInspector()).thenReturn("inspector-a");
        var assigned = assignment("inspector-a", null);
        when(assignments.findByCommissionId("commission")).thenReturn(Optional.of(assigned));
        assertThat(authorization.requireCommission("commission")).isEqualTo(assigned);
    }

    @Test
    void eligibleInspectorCannotAccessOtherInspectorsCommission() {
        when(eligibility.requireEligibleInspector()).thenReturn("inspector-a");
        when(assignments.findByCommissionId("commission"))
                .thenReturn(Optional.of(assignment("inspector-b", null)));
        assertThatThrownBy(() -> authorization.requireCommission("commission"))
                .isInstanceOf(InspectionAssignmentAccessDeniedException.class);
    }

    @Test
    void missingCommissionIsDeniedWithoutRevealingExistence() {
        when(eligibility.requireEligibleInspector()).thenReturn("inspector-a");
        assertThatThrownBy(() -> authorization.requireCommission("missing"))
                .isInstanceOf(InspectionAssignmentAccessDeniedException.class).hasMessage("No authorized inspection assignment");
    }

    @Test
    void historicalAssignmentNoLongerGrantsAccess() {
        when(eligibility.requireEligibleInspector()).thenReturn("inspector-a");
        when(assignments.findByCommissionId("commission"))
                .thenReturn(Optional.of(assignment("inspector-a", Instant.now())));
        assertThatThrownBy(() -> authorization.requireCommission("commission"))
                .isInstanceOf(InspectionAssignmentAccessDeniedException.class);
    }

    @Test
    void milestoneLookupUsesBothRouteIdentifiersAndChecksOwner() {
        when(eligibility.requireEligibleInspector()).thenReturn("inspector-a");
        when(assignments.findActive("contract", "milestone"))
                .thenReturn(Optional.of(assignment("inspector-a", null)));
        assertThat(authorization.requireMilestone("contract", "milestone").inspectorId()).isEqualTo("inspector-a");
        assertThatThrownBy(() -> authorization.requireMilestone("other-contract", "milestone"))
                .isInstanceOf(InspectionAssignmentAccessDeniedException.class);
    }

    @Test
    void milestoneLookupRejectsOtherOwner() {
        when(eligibility.requireEligibleInspector()).thenReturn("inspector-a");
        when(assignments.findActive("contract", "milestone"))
                .thenReturn(Optional.of(assignment("inspector-b", null)));
        assertThatThrownBy(() -> authorization.requireMilestone("contract", "milestone"))
                .isInstanceOf(InspectionAssignmentAccessDeniedException.class);
    }

    @Test
    void eligibilityFailureCannotBeBypassedByOwningAssignment() {
        when(eligibility.requireEligibleInspector()).thenThrow(new InspectorNotEligibleException());
        assertThatThrownBy(() -> authorization.requireCommission("commission"))
                .isInstanceOf(InspectorNotEligibleException.class);
        verifyNoInteractions(assignments);
    }

    @Test
    void dependencyOutageCannotBeBypassedByOwningAssignment() {
        when(eligibility.requireEligibleInspector()).thenThrow(new EligibilityUnavailableException());
        assertThatThrownBy(() -> authorization.requireMilestone("contract", "milestone"))
                .isInstanceOf(EligibilityUnavailableException.class);
        verifyNoInteractions(assignments);
    }

    private InspectionAssignment assignment(String inspector, Instant endedAt) {
        return new InspectionAssignment("commission", "contract", "milestone", inspector,
                Instant.parse("2026-10-08T00:00:00Z"), endedAt, "test-fixture", null);
    }
}
