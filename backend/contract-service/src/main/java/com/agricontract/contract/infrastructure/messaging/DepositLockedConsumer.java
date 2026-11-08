package com.agricontract.contract.infrastructure.messaging;

import com.agricontract.contract.application.dto.DepositLockedCommand;
import com.agricontract.contract.application.exception.InvalidEventPayloadException;
import com.agricontract.contract.application.usecase.ActivateContractUseCase;
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
 * escrow.deposit_locked (golden-flow-events.yaml escrowDepositLocked) → ActivateContractUseCase (#103). The payload
 * is parsed here against the schema (InvalidEventPayloadException: DLQ, no retry). The eventId is claimed and the
 * contract activated, with contract.activated in the outbox, in one transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DepositLockedConsumer {
    static final String CONSUMER_NAME = "contract-service.deposit-locked";
    static final String EVENT_TYPE = "escrow.deposit_locked";
    static final String PRODUCER = "escrow-service";

    private static final Set<String> FIELDS = Set.of("contractId", "buyerDepositState", "sellerDepositState");
    private static final Set<String> STATES = Set.of("DEPOSIT_LOCKED", "DEPOSIT_RELEASED", "DEPOSIT_SEIZED");
    private static final Pattern UUID =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final EventEnvelopeCodec codec;
    private final ProcessedEventStore processedEvents;
    private final ActivateContractUseCase activateContract;
    private final TransactionTemplate transactionTemplate;

    @RabbitListener(queues = RabbitMQConfig.DEPOSIT_LOCKED_QUEUE)
    public void onDepositLocked(Message message) {
        EventEnvelope envelope = parse(message.getBody());
        DepositLockedCommand command = toCommand(envelope);
        transactionTemplate.executeWithoutResult(status -> {
            if (!processedEvents.markProcessed(CONSUMER_NAME, envelope)) {
                log.info("event=deposit_locked_duplicate eventId={} contractId={}", envelope.eventId(),
                        command.contractId());
                return;
            }
            activateContract.execute(command);
        });
    }

    private EventEnvelope parse(byte[] body) {
        try {
            return codec.parse(body);
        } catch (InvalidEnvelopeException | IllegalArgumentException e) {
            throw new InvalidEventPayloadException("Malformed event envelope", e);
        }
    }

    DepositLockedCommand toCommand(EventEnvelope envelope) {
        if (!EVENT_TYPE.equals(envelope.eventType()) || !PRODUCER.equals(envelope.producer())
                || envelope.eventVersion() != 1) {
            throw new InvalidEventPayloadException("Unexpected event type, producer or version", null);
        }
        Map<String, Object> payload = envelope.payload();
        if (!payload.keySet().equals(FIELDS)) {
            throw new InvalidEventPayloadException("Payload fields do not match escrow.deposit_locked", null);
        }
        if (!(payload.get("contractId") instanceof String contractId) || !UUID.matcher(contractId).matches()) {
            throw new InvalidEventPayloadException("contractId is not a UUID", null);
        }
        Object buyer = payload.get("buyerDepositState");
        Object seller = payload.get("sellerDepositState");
        if (!(buyer instanceof String buyerState) || !STATES.contains(buyerState)
                || (seller != null && !(seller instanceof String s && STATES.contains(s)))) {
            throw new InvalidEventPayloadException("Deposit states are not in the schema enum", null);
        }
        String eventId = envelope.eventId().toString();
        return new DepositLockedCommand(eventId,
                envelope.correlationId() != null ? envelope.correlationId() : eventId,
                contractId.toLowerCase(), buyerState, (String) seller);
    }
}
