package com.agricontract.eventing.inbox;

import com.agricontract.eventing.EventEnvelope;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumer-side dedupe on processed_event. Must run in the handler's transaction: the row commits with the
 * handler's work, or rolls back with it so a redelivery is processed again.
 */
@RequiredArgsConstructor
public class ProcessedEventStore {
    private static final int MAX_CONSUMER_NAME_LENGTH = 100; // processed_event.consumer_name VARCHAR(100)

    private final JdbcTemplate jdbcTemplate;

    /**
     * Claims (consumerName, eventId) for this transaction. Returns false when it was already processed: the caller
     * acks and returns, never throws, or the duplicate would be retried into the DLQ.
     * A concurrent claim of the same pair blocks on the primary key until the first transaction ends.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean markProcessed(String consumerName, EventEnvelope envelope) {
        // INSERT IGNORE would silently truncate an over-long name, so two consumers could share one dedupe key.
        if (consumerName == null || consumerName.isBlank()) {
            throw new IllegalArgumentException("consumerName must not be blank");
        }
        if (consumerName.length() > MAX_CONSUMER_NAME_LENGTH) {
            throw new IllegalArgumentException("consumerName must be at most " + MAX_CONSUMER_NAME_LENGTH + " characters");
        }
        int inserted = jdbcTemplate.update(
                "INSERT IGNORE INTO processed_event (consumer_name, event_id, event_type) VALUES (?, ?, ?)",
                consumerName,
                envelope.eventId().toString(),
                envelope.eventType()
        );
        return inserted == 1;
    }
}
