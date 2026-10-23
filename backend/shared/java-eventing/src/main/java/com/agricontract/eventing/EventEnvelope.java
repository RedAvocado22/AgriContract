package com.agricontract.eventing;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public record EventEnvelope(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String producer,
        String aggregateId,
        String correlationId,
        String causationId,
        EventSubject subject,
        Map<String, Object> payload
) {
    private static final Pattern EVENT_TYPE = Pattern.compile("^[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+$");

    public EventEnvelope {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId is required");
        }

        requireText(eventType, "eventType");
        if (!EVENT_TYPE.matcher(eventType).matches()) {
            throw new IllegalArgumentException("eventType must match pattern " + EVENT_TYPE.pattern());
        }

        if (eventVersion < 1) {
            throw new IllegalArgumentException("eventVersion must be >= 1");
        }

        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt is required");
        }

        requireText(producer, "producer");
        requireText(aggregateId, "aggregateId");
        optionalText(correlationId, "correlationId");
        optionalText(causationId, "causationId");

        if (payload == null) {
            throw new IllegalArgumentException("payload is required");
        }
    }

    static void requireText(String value, String field) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    static void optionalText(String value, String field) {
        if (value != null && value.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
    }
}
