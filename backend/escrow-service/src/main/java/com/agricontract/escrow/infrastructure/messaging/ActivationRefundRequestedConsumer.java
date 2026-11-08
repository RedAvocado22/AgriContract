package com.agricontract.escrow.infrastructure.messaging;

import com.agricontract.escrow.application.dto.ActivationRefundCommand;
import com.agricontract.escrow.application.exception.InvalidEventPayloadException;
import com.agricontract.escrow.application.usecase.RefundActivationDepositsUseCase;
import com.agricontract.escrow.infrastructure.config.RabbitMQConfig;
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

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * contract.activation_refund_requested (golden-flow-events.yaml contractActivationRefundRequested, D42) → return the
 * activation deposits. Parsed here against the schema (InvalidEventPayloadException: DLQ, no retry); the eventId is
 * claimed and the legs stopped in one transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivationRefundRequestedConsumer {

    static final String CONSUMER_NAME = "escrow-service.activation-refund-requested";
    static final String EVENT_TYPE = "contract.activation_refund_requested";
    static final String PRODUCER = "contract-service";

    private static final Set<String> FIELDS = Set.of("contractId", "requestedBy", "trigger", "requestedAt");
    private static final Set<String> PARTIES = Set.of("BUYER", "SELLER");
    private static final Set<String> TRIGGERS = Set.of("BOTH_PARTIES", "ONE_PARTY_AFTER_WINDOW");
    private static final Pattern UUID =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final EventEnvelopeCodec codec;
    private final ProcessedEventStore processedEvents;
    private final RefundActivationDepositsUseCase refundDeposits;
    private final TransactionTemplate transactionTemplate;

    @RabbitListener(queues = RabbitMQConfig.ACTIVATION_REFUND_QUEUE)
    public void onRefundRequested(Message message) {
        EventEnvelope envelope = parse(message.getBody());
        ActivationRefundCommand command = toCommand(envelope);
        transactionTemplate.executeWithoutResult(status -> {
            if (!processedEvents.markProcessed(CONSUMER_NAME, envelope)) {
                log.info("event=activation_refund_duplicate eventId={} contractId={}", envelope.eventId(),
                        command.contractId());
                return;
            }
            refundDeposits.execute(command);
        });
    }

    private EventEnvelope parse(byte[] body) {
        try {
            return codec.parse(body);
        } catch (InvalidEnvelopeException | IllegalArgumentException e) {
            throw new InvalidEventPayloadException("Malformed event envelope");
        }
    }

    ActivationRefundCommand toCommand(EventEnvelope envelope) {
        if (!EVENT_TYPE.equals(envelope.eventType()) || !PRODUCER.equals(envelope.producer())
                || envelope.eventVersion() != 1) {
            throw new InvalidEventPayloadException("Unexpected event type, producer or version");
        }
        Map<String, Object> payload = envelope.payload();
        if (!payload.keySet().equals(FIELDS)) {
            throw new InvalidEventPayloadException("Payload fields do not match " + EVENT_TYPE);
        }
        if (!(payload.get("contractId") instanceof String contractId) || !UUID.matcher(contractId).matches()) {
            throw new InvalidEventPayloadException("contractId is not a UUID");
        }
        if (!(payload.get("requestedBy") instanceof List<?> parties) || parties.isEmpty() || parties.size() > 2
                || !PARTIES.containsAll(parties) || new HashSet<>(parties).size() != parties.size()) {
            throw new InvalidEventPayloadException("requestedBy must be 1 or 2 distinct parties");
        }
        if (!(payload.get("trigger") instanceof String trigger) || !TRIGGERS.contains(trigger)) {
            throw new InvalidEventPayloadException("trigger is not in the schema enum");
        }
        if (!(payload.get("requestedAt") instanceof String requestedAt)) {
            throw new InvalidEventPayloadException("requestedAt is not a date-time");
        }
        try {
            OffsetDateTime.parse(requestedAt);
        } catch (DateTimeParseException e) {
            throw new InvalidEventPayloadException("requestedAt is not a date-time");
        }
        String eventId = envelope.eventId().toString();
        return new ActivationRefundCommand(eventId,
                envelope.correlationId() != null ? envelope.correlationId() : eventId,
                contractId.toLowerCase(), parties.stream().map(String::valueOf).toList(), trigger);
    }
}
