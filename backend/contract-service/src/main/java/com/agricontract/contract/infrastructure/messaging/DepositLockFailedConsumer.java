package com.agricontract.contract.infrastructure.messaging;

import com.agricontract.contract.application.dto.DepositLockFailedCommand;
import com.agricontract.contract.application.exception.InvalidEventPayloadException;
import com.agricontract.contract.application.usecase.RecordDepositLockFailureUseCase;
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

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * escrow.deposit_lock_failed (golden-flow-events.yaml escrowDepositLockFailed) → RecordDepositLockFailureUseCase
 * (#234). The payload is parsed here against the schema (InvalidEventPayloadException: DLQ, no retry). The eventId is
 * claimed and the failure recorded, with its notification in the outbox, in one transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DepositLockFailedConsumer {
    static final String CONSUMER_NAME = "contract-service.deposit-lock-failed";
    static final String EVENT_TYPE = "escrow.deposit_lock_failed";
    static final String PRODUCER = "escrow-service";

    private static final Set<String> FIELDS =
            Set.of("contractId", "failedLeg", "buyerDepositState", "sellerDepositState", "reason");
    private static final Set<String> FAILED_LEGS = Set.of("BUYER", "SELLER", "BOTH");
    private static final Pattern UUID =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final EventEnvelopeCodec codec;
    private final ProcessedEventStore processedEvents;
    private final RecordDepositLockFailureUseCase recordFailure;
    private final TransactionTemplate transactionTemplate;

    @RabbitListener(queues = RabbitMQConfig.DEPOSIT_LOCK_FAILED_QUEUE)
    public void onDepositLockFailed(Message message) {
        EventEnvelope envelope = parse(message.getBody());
        DepositLockFailedCommand command = toCommand(envelope);
        transactionTemplate.executeWithoutResult(status -> {
            if (!processedEvents.markProcessed(CONSUMER_NAME, envelope)) {
                log.info("event=deposit_lock_failed_duplicate eventId={} contractId={}", envelope.eventId(),
                        command.contractId());
                return;
            }
            recordFailure.execute(command);
        });
    }

    private EventEnvelope parse(byte[] body) {
        try {
            return codec.parse(body);
        } catch (InvalidEnvelopeException | IllegalArgumentException e) {
            throw new InvalidEventPayloadException("Malformed event envelope", e);
        }
    }

    DepositLockFailedCommand toCommand(EventEnvelope envelope) {
        if (!EVENT_TYPE.equals(envelope.eventType()) || !PRODUCER.equals(envelope.producer())
                || envelope.eventVersion() != 1) {
            throw new InvalidEventPayloadException("Unexpected event type, producer or version", null);
        }
        Map<String, Object> payload = envelope.payload();
        if (!payload.keySet().equals(FIELDS)) {
            throw new InvalidEventPayloadException("Payload fields do not match escrow.deposit_lock_failed", null);
        }
        if (!(payload.get("contractId") instanceof String contractId) || !UUID.matcher(contractId).matches()) {
            throw new InvalidEventPayloadException("contractId is not a UUID", null);
        }
        if (!(payload.get("failedLeg") instanceof String failedLeg) || !FAILED_LEGS.contains(failedLeg)) {
            throw new InvalidEventPayloadException("failedLeg is not BUYER, SELLER or BOTH", null);
        }
        if (!(payload.get("buyerDepositState") instanceof String buyerState)
                || !(payload.get("sellerDepositState") instanceof String sellerState)) {
            throw new InvalidEventPayloadException("Deposit states must be strings", null);
        }
        if (!(payload.get("reason") instanceof String reason) || reason.isEmpty()) {
            throw new InvalidEventPayloadException("reason is required", null);
        }
        String eventId = envelope.eventId().toString();
        return new DepositLockFailedCommand(eventId,
                envelope.correlationId() != null ? envelope.correlationId() : eventId,
                contractId.toLowerCase(), failedLeg, buyerState, sellerState, reason);
    }
}
