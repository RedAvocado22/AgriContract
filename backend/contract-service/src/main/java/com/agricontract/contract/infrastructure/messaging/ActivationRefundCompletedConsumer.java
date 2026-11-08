package com.agricontract.contract.infrastructure.messaging;

import com.agricontract.contract.application.exception.InvalidEventPayloadException;
import com.agricontract.contract.application.usecase.CompleteActivationFailureUseCase;
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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * escrow.activation_refund_completed (golden-flow-events.yaml escrowActivationRefundCompleted) →
 * CompleteActivationFailureUseCase. Parsed against the schema (DLQ, no retry); claimed and applied in one transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivationRefundCompletedConsumer {
    static final String CONSUMER_NAME = "contract-service.activation-refund-completed";
    static final String EVENT_TYPE = "escrow.activation_refund_completed";
    static final String PRODUCER = "escrow-service";

    private static final Set<String> FIELDS = Set.of("contractId", "returnedLegs", "completedAt");
    private static final Set<String> LEG_FIELDS = Set.of("fundType", "userId", "amount", "ledgerEntryId");
    private static final Pattern UUID =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final EventEnvelopeCodec codec;
    private final ProcessedEventStore processedEvents;
    private final CompleteActivationFailureUseCase completeFailure;
    private final TransactionTemplate transactionTemplate;

    @RabbitListener(queues = RabbitMQConfig.ACTIVATION_REFUND_COMPLETED_QUEUE)
    public void onRefundCompleted(Message message) {
        EventEnvelope envelope = parse(message.getBody());
        String contractId = contractId(envelope);
        String eventId = envelope.eventId().toString();
        String correlationId = envelope.correlationId() != null ? envelope.correlationId() : eventId;
        transactionTemplate.executeWithoutResult(status -> {
            if (!processedEvents.markProcessed(CONSUMER_NAME, envelope)) {
                log.info("event=activation_refund_completed_duplicate eventId={} contractId={}", eventId, contractId);
                return;
            }
            completeFailure.execute(contractId, eventId, correlationId);
        });
    }

    private EventEnvelope parse(byte[] body) {
        try {
            return codec.parse(body);
        } catch (InvalidEnvelopeException | IllegalArgumentException e) {
            throw new InvalidEventPayloadException("Malformed event envelope", e);
        }
    }

    String contractId(EventEnvelope envelope) {
        if (!EVENT_TYPE.equals(envelope.eventType()) || !PRODUCER.equals(envelope.producer())
                || envelope.eventVersion() != 1) {
            throw new InvalidEventPayloadException("Unexpected event type, producer or version", null);
        }
        Map<String, Object> payload = envelope.payload();
        if (!payload.keySet().equals(FIELDS)) {
            throw new InvalidEventPayloadException("Payload fields do not match " + EVENT_TYPE, null);
        }
        if (!(payload.get("contractId") instanceof String contractId) || !UUID.matcher(contractId).matches()) {
            throw new InvalidEventPayloadException("contractId is not a UUID", null);
        }
        if (!(payload.get("returnedLegs") instanceof List<?> legs) || legs.size() > 2
                || !legs.stream().allMatch(l -> l instanceof Map<?, ?> m && m.keySet().equals(LEG_FIELDS))) {
            throw new InvalidEventPayloadException("returnedLegs does not match the schema", null);
        }
        if (!(payload.get("completedAt") instanceof String)) {
            throw new InvalidEventPayloadException("completedAt is required", null);
        }
        return contractId.toLowerCase();
    }
}
