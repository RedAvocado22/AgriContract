package com.agricontract.user.infrastructure.messaging;

import com.agricontract.eventing.EventEnvelope;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** reputation.locked / reputation.unlocked as reputation-service would publish them (frozen schema checked in tests). */
final class ReputationLockEvents {

    private ReputationLockEvents() {
    }

    static EventEnvelope locked(String userId, Instant lockedUntil, long lockRevision) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("lockedUntil", lockedUntil.toString());
        payload.put("reasonCode", "CONTRACT_BREACH");
        payload.put("lockRevision", lockRevision);
        payload.put("occurredAt", "2026-11-01T03:00:00Z");
        return envelope("reputation.locked", userId, payload);
    }

    static EventEnvelope unlocked(String userId, Instant effectiveLockedUntil, long lockRevision) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("effectiveLockedUntil", effectiveLockedUntil == null ? null : effectiveLockedUntil.toString());
        payload.put("reasonCode", "UNLOCK_EARLY");
        payload.put("lockRevision", lockRevision);
        payload.put("occurredAt", "2026-11-01T04:00:00Z");
        return envelope("reputation.unlocked", userId, payload);
    }

    static EventEnvelope withPayload(EventEnvelope source, Map<String, Object> payload) {
        return new EventEnvelope(source.eventId(), source.eventType(), source.eventVersion(), source.occurredAt(),
                source.producer(), source.aggregateId(), source.correlationId(), source.causationId(), null, payload);
    }

    private static EventEnvelope envelope(String eventType, String userId, Map<String, Object> payload) {
        return new EventEnvelope(UUID.randomUUID(), eventType, 1, Instant.parse("2026-11-01T03:00:00Z"),
                "reputation-service", userId, UUID.randomUUID().toString(), null, null, payload);
    }
}
