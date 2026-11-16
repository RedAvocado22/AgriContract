package com.agricontract.reputation.infrastructure.messaging;

import com.agricontract.eventing.EventEnvelope;
import com.agricontract.eventing.outbox.OutboxWriter;
import com.agricontract.reputation.application.port.LockEventPublisher;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OutboxLockEventPublisher implements LockEventPublisher {
    private final OutboxWriter outbox;

    public OutboxLockEventPublisher(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    @Override
    public void publish(UUID userId, boolean locked, Instant until, String reason, long revision,
                        Instant now, String correlationId, UUID cause) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId.toString());
        payload.put(locked ? "lockedUntil" : "effectiveLockedUntil", until == null ? null : until.toString());
        payload.put("reasonCode", reason);
        payload.put("lockRevision", revision);
        payload.put("occurredAt", now.toString());
        outbox.append(new EventEnvelope(UUID.randomUUID(), locked ? "reputation.locked" : "reputation.unlocked",
                1, now, "reputation-service", userId.toString(), correlationId,
                cause == null ? null : cause.toString(), null, payload));
    }
}
