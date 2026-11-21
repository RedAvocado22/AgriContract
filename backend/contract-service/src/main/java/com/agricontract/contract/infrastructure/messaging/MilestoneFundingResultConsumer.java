package com.agricontract.contract.infrastructure.messaging;

import com.agricontract.contract.application.dto.MilestoneFundingResultCommand;
import com.agricontract.contract.application.exception.InvalidEventPayloadException;
import com.agricontract.contract.application.usecase.RecordMilestoneFundingResultUseCase;
import com.agricontract.contract.infrastructure.config.RabbitMQConfig;
import com.agricontract.eventing.EventEnvelope;
import com.agricontract.eventing.EventEnvelopeCodec;
import com.agricontract.eventing.InvalidEnvelopeException;
import com.agricontract.eventing.inbox.ProcessedEventStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * escrow.milestone_funded and escrow.milestone_funding_failed (golden-flow-events.yaml escrowMilestoneFunded,
 * escrowMilestoneFundingFailed, D64) → RecordMilestoneFundingResultUseCase. The payload is parsed here against the
 * schema (InvalidEventPayloadException: DLQ, no retry). The eventId is claimed and the milestone updated in one
 * transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MilestoneFundingResultConsumer {
    static final String FUNDED_CONSUMER = "contract-service.milestone-funded";
    static final String FAILED_CONSUMER = "contract-service.milestone-funding-failed";
    static final String FUNDED = "escrow.milestone_funded";
    static final String FAILED = "escrow.milestone_funding_failed";
    static final String PRODUCER = "escrow-service";

    private static final Set<String> FUNDED_FIELDS = Set.of("contractId", "milestoneId", "lockedAt");
    private static final Set<String> FAILED_FIELDS = Set.of("contractId", "milestoneId", "reason");
    private static final Pattern UUID =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final EventEnvelopeCodec codec;
    private final ProcessedEventStore processedEvents;
    private final RecordMilestoneFundingResultUseCase recordResult;
    private final TransactionTemplate transactionTemplate;

    @RabbitListener(queues = RabbitMQConfig.MILESTONE_FUNDED_QUEUE)
    public void onFunded(Message message) {
        EventEnvelope envelope = parse(message.getBody());
        MilestoneFundingResultCommand command = toFundedCommand(envelope);
        transactionTemplate.executeWithoutResult(status -> {
            if (processedEvents.markProcessed(FUNDED_CONSUMER, envelope)) {
                recordResult.funded(command);
            } else {
                log.info("event=milestone_funded_event_duplicate eventId={}", envelope.eventId());
            }
        });
    }

    @RabbitListener(queues = RabbitMQConfig.MILESTONE_FUNDING_FAILED_QUEUE)
    public void onFundingFailed(Message message) {
        EventEnvelope envelope = parse(message.getBody());
        MilestoneFundingResultCommand command = toFailedCommand(envelope);
        transactionTemplate.executeWithoutResult(status -> {
            if (processedEvents.markProcessed(FAILED_CONSUMER, envelope)) {
                recordResult.failed(command);
            } else {
                log.info("event=milestone_funding_failed_event_duplicate eventId={}", envelope.eventId());
            }
        });
    }

    private EventEnvelope parse(byte[] body) {
        try {
            return codec.parse(body);
        } catch (InvalidEnvelopeException | IllegalArgumentException e) {
            throw new InvalidEventPayloadException("Malformed event envelope", e);
        }
    }

    MilestoneFundingResultCommand toFundedCommand(EventEnvelope envelope) {
        Map<String, Object> payload = payload(envelope, FUNDED, FUNDED_FIELDS);
        if (!(payload.get("lockedAt") instanceof String lockedAt)) {
            throw new InvalidEventPayloadException("lockedAt is not a date-time", null);
        }
        Instant locked;
        try {
            // Stored as TIMESTAMP(6): keep microseconds so the stored instant is the one compared.
            locked = Instant.parse(lockedAt).truncatedTo(ChronoUnit.MICROS);
        } catch (DateTimeParseException e) {
            throw new InvalidEventPayloadException("lockedAt is not a date-time", e);
        }
        return new MilestoneFundingResultCommand(envelope.eventId().toString(), id(payload, "contractId"),
                id(payload, "milestoneId"), locked, null);
    }

    MilestoneFundingResultCommand toFailedCommand(EventEnvelope envelope) {
        Map<String, Object> payload = payload(envelope, FAILED, FAILED_FIELDS);
        if (!(payload.get("reason") instanceof String reason) || reason.isEmpty()) {
            throw new InvalidEventPayloadException("reason must be a non-empty string", null);
        }
        return new MilestoneFundingResultCommand(envelope.eventId().toString(), id(payload, "contractId"),
                id(payload, "milestoneId"), null, reason);
    }

    private static Map<String, Object> payload(EventEnvelope envelope, String eventType, Set<String> fields) {
        if (!eventType.equals(envelope.eventType()) || !PRODUCER.equals(envelope.producer())
                || envelope.eventVersion() != 1) {
            throw new InvalidEventPayloadException("Unexpected event type, producer or version", null);
        }
        Map<String, Object> payload = envelope.payload();
        if (!payload.keySet().equals(fields)) {
            throw new InvalidEventPayloadException("Payload fields do not match " + eventType, null);
        }
        return payload;
    }

    private static String id(Map<String, Object> payload, String field) {
        if (!(payload.get(field) instanceof String value) || !UUID.matcher(value).matches()) {
            throw new InvalidEventPayloadException(field + " is not a UUID", null);
        }
        return value.toLowerCase();
    }
}
