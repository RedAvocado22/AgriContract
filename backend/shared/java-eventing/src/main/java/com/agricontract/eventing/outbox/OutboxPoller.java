package com.agricontract.eventing.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@RequiredArgsConstructor
public class OutboxPoller {
    private static final int MAX_ERROR_LENGTH = 1000; // event_outbox.last_error VARCHAR(1000)

    private final JdbcTemplate jdbcTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final TransactionTemplate transactionTemplate;
    private final String exchange;
    private final int batchSize;
    private final Duration confirmTimeout;

    private record OutboxRow(long id, String eventId, String eventType, String envelope) {
    }

    @Scheduled(fixedDelayString = "${agricontract.eventing.outbox.poll-interval:PT1S}")
    public void poll() {
        transactionTemplate.executeWithoutResult(status -> {
            List<OutboxRow> rows = jdbcTemplate.query("""
                            SELECT id, event_id, event_type, envelope FROM event_outbox
                            WHERE status = 'PENDING' ORDER BY id LIMIT ?
                            FOR UPDATE SKIP LOCKED""",
                    (rs, rowNum) -> new OutboxRow(
                            rs.getLong("id"),
                            rs.getString("event_id"),
                            rs.getString("event_type"),
                            rs.getString("envelope")
                    ),
                    batchSize
            );

            for (OutboxRow row : rows) {
                try {
                    publish(row);
                    jdbcTemplate.update(
                            "UPDATE event_outbox SET status = 'PUBLISHED', published_at = NOW(3) WHERE id = ?",
                            row.id()
                    );
                    log.debug("Published outbox event {} ({}) to {}", row.eventId(), row.eventType(), exchange);
                } catch (Exception e) {
                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    String error;
                    if (e.getMessage() == null) {
                        error = e.getClass().getSimpleName();
                    } else {
                        error = e.getMessage();
                    }
                    error = error.substring(0, Math.min(MAX_ERROR_LENGTH, error.length()));
                    log.warn("Failed to publish outbox event {} ({}) to {}, stopping this batch, will retry next poll: {}",
                            row.eventId(), row.eventType(), exchange, error);
                    jdbcTemplate.update(
                            "UPDATE event_outbox SET attempts = attempts + 1, last_error = ? WHERE id = ?",
                            error, row.id()
                    );
                    // Stop at the first failure, as node-eventing does: later rows are not sent ahead of this one,
                    // and a stalled broker costs one confirm timeout per poll instead of one per row.
                    return;
                }
            }
        });
    }

    private void publish(OutboxRow row) throws Exception {
        Message message = MessageBuilder.withBody(row.envelope().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(row.eventId())
                .build();

        CorrelationData cd = new CorrelationData(row.eventId());
        rabbitTemplate.send(exchange, row.eventType(), message, cd);

        CorrelationData.Confirm confirm;
        try {
            confirm = cd.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new IllegalStateException("No publisher confirm within " + confirmTimeout);
        }

        if (!confirm.isAck()) {
            throw new IllegalStateException("Broker nacked publish"
                    + (confirm.getReason() != null ? ": " + confirm.getReason() : ""));
        } else if (cd.getReturned() != null) {
            throw new IllegalStateException("Unroutable: no queue bound for routing key " + row.eventType());
        }
    }
}
