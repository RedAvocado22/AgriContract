package com.agricontract.inspection.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record AssignmentRequest(String contractId, String milestoneId, String reason,
                                int reportDueBusinessDays, String initiatedByActorId, Instant requestedAt) {
    private static final Set<String> FIELDS = Set.of("contractId", "milestoneId", "reason",
            "reportDueBusinessDays", "initiatedByActorId", "requestedAt");
    private static final Set<String> REASONS = Set.of("DISPUTE_ROUTED", "MANDATORY_INSPECTION",
            "FORCE_MAJEURE_CONTEST", "DECISION_CONTESTED", "LEVEL2_BUFFER_EXPIRED");

    public AssignmentRequest {
        contractId = id(contractId); milestoneId = id(milestoneId);
        if (!REASONS.contains(reason) || reportDueBusinessDays < 1 || reportDueBusinessDays > 30
                || requestedAt == null) throw new IllegalArgumentException("Invalid assignment request");
        if (initiatedByActorId != null) initiatedByActorId = id(initiatedByActorId);
    }

    public static AssignmentRequest from(Map<String, Object> payload) {
        if (!payload.keySet().equals(FIELDS) || !(payload.get("reportDueBusinessDays") instanceof Integer days))
            throw new IllegalArgumentException("Invalid assignment payload");
        return new AssignmentRequest(text(payload.get("contractId")), text(payload.get("milestoneId")),
                text(payload.get("reason")), days,
                payload.get("initiatedByActorId") == null ? null : text(payload.get("initiatedByActorId")),
                java.time.OffsetDateTime.parse(text(payload.get("requestedAt"))).toInstant());
    }

    private static String text(Object value) {
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException("Missing field");
        return text;
    }

    private static String id(String value) {
        String canonical = UUID.fromString(value).toString();
        if (!canonical.equalsIgnoreCase(value)) throw new IllegalArgumentException("Invalid UUID");
        return canonical;
    }
}
