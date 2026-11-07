package com.agricontract.inspection.application.dto;

public record Level15AssignmentRequestedCommand(String contractId, String milestoneId,
        int reportDueBusinessDays, String initiatedByActorId, String sourceEventId) {
}
