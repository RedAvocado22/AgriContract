package com.agricontract.eventing.outbox;

import com.agricontract.eventing.EventEnvelope;
import com.agricontract.eventing.EventEnvelopeCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class OutboxWriter {

    private final JdbcTemplate jdbc;
    private final EventEnvelopeCodec codec;

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(EventEnvelope envelope) {
        jdbc.update(
                "INSERT INTO event_outbox (event_id, event_type, aggregate_id, envelope) VALUES (?, ?, ?, ?)",
                envelope.eventId().toString(),
                envelope.eventType(),
                envelope.aggregateId(),
                codec.toJson(envelope)
        );
    }
}
