package com.agricontract.inspection.domain.model;

import java.time.Instant;
import java.time.LocalDate;

/** Durable D49 request; original reason and waiting time survive every retry. */
public record WaitingAssignment(String contractId, String milestoneId, Reason reason,
        int reportDueBusinessDays, String assignedByService, String initiatedByActorId,
        Instant waitingSince, Instant lastAttemptAt, LocalDate lastReminderOn) {
    public enum Reason { NO_ELIGIBLE, ALL_EXCLUDED }
    public record Key(String contractId, String milestoneId) {}
    public Key key() { return new Key(contractId, milestoneId); }
}
