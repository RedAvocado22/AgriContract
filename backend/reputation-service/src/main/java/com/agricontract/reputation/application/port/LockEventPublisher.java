package com.agricontract.reputation.application.port;

import java.time.Instant;
import java.util.UUID;

public interface LockEventPublisher {
    void publish(UUID userId, boolean locked, Instant effectiveUntil, String reason,
                 long revision, Instant occurredAt, String correlationId, UUID causationId);
}
