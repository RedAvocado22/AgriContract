package com.agricontract.escrow.infrastructure.messaging;

import com.agricontract.escrow.application.dto.DepositLockRetryCommand;
import com.agricontract.escrow.application.exception.InvalidEventPayloadException;
import com.agricontract.escrow.application.usecase.RestartDepositLocksUseCase;
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
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * contract.deposit_lock_retry_requested (golden-flow-events.yaml contractDepositLockRetryRequested) → a new retry
 * cycle for the failed deposit legs (D42). Parsed here against the schema (InvalidEventPayloadException: DLQ, no
 * retry); the eventId is claimed and the legs restarted in one transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DepositLockRetryConsumer {

    static final String CONSUMER_NAME = "escrow-service.deposit-lock-retry-requested";
    static final String EVENT_TYPE = "contract.deposit_lock_retry_requested";
    static final String PRODUCER = "contract-service";

    private static final Set<String> FIELDS = Set.of("contractId", "operatorUserId", "requestedAt");
    private static final Pattern UUID =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final EventEnvelopeCodec codec;
    private final ProcessedEventStore processedEvents;
    private final RestartDepositLocksUseCase restartDepositLocks;
    private final TransactionTemplate transactionTemplate;

    @RabbitListener(queues = RabbitMQConfig.DEPOSIT_LOCK_RETRY_QUEUE)
    public void onRetryRequested(Message message) {
        EventEnvelope envelope = parse(message.getBody());
        DepositLockRetryCommand command = toCommand(envelope);
        transactionTemplate.executeWithoutResult(status -> {
            if (!processedEvents.markProcessed(CONSUMER_NAME, envelope)) {
                log.info("event=deposit_lock_retry_duplicate eventId={} contractId={}", envelope.eventId(),
                        command.contractId());
                return;
            }
            restartDepositLocks.execute(command);
        });
    }

    private EventEnvelope parse(byte[] body) {
        try {
            return codec.parse(body);
        } catch (InvalidEnvelopeException | IllegalArgumentException e) {
            throw new InvalidEventPayloadException("Malformed event envelope");
        }
    }

    DepositLockRetryCommand toCommand(EventEnvelope envelope) {
        if (!EVENT_TYPE.equals(envelope.eventType()) || !PRODUCER.equals(envelope.producer())
                || envelope.eventVersion() != 1) {
            throw new InvalidEventPayloadException("Unexpected event type, producer or version");
        }
        Map<String, Object> payload = envelope.payload();
        if (!payload.keySet().equals(FIELDS)) {
            throw new InvalidEventPayloadException("Payload fields do not match contract.deposit_lock_retry_requested");
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
        return new DepositLockRetryCommand(eventId,
                envelope.correlationId() != null ? envelope.correlationId() : eventId,
                uuid(payload, "contractId"), uuid(payload, "operatorUserId"));
    }

    private static String uuid(Map<String, Object> payload, String field) {
        if (!(payload.get(field) instanceof String text) || !UUID.matcher(text).matches()) {
            throw new InvalidEventPayloadException(field + " is not a UUID");
        }
        return text.toLowerCase();
    }
}
