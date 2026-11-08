package com.agricontract.escrow.infrastructure.messaging;

import com.agricontract.escrow.application.dto.ReturnResultCommand;
import com.agricontract.escrow.application.exception.InvalidEventPayloadException;
import com.agricontract.escrow.application.usecase.RecordReturnResultUseCase;
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

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * bank.refund_completed / _failed and bank.release_completed / _failed (golden-flow-events.yaml) for activation deposit
 * returns (#247). Same parse as the lock results: every required field, milestoneId the only optional one, nothing
 * else; the entryType must be the operation's. Bad payload → DLQ without retry.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BankReturnResultConsumer {

    static final String PRODUCER = "bank-service";

    private static final Set<String> COMMON = Set.of("contractId", "userId", "entryType", "fundType",
            "remedyDecisionId", "remedyLegId", "amount", "sourceEventId");
    private static final Pattern UUID =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern POSITIVE_MONEY =
            Pattern.compile("^(?:0\\.(?:0[1-9]|[1-9][0-9])|[1-9][0-9]*\\.[0-9]{2})$");

    private final EventEnvelopeCodec codec;
    private final ProcessedEventStore processedEvents;
    private final RecordReturnResultUseCase recordReturnResult;
    private final TransactionTemplate transactionTemplate;

    @RabbitListener(queues = RabbitMQConfig.REFUND_COMPLETED_QUEUE)
    public void onRefundCompleted(Message message) {
        handle("escrow-service.bank-refund-completed", message, "bank.refund_completed", "REFUND_TO_BUYER", true);
    }

    @RabbitListener(queues = RabbitMQConfig.REFUND_FAILED_QUEUE)
    public void onRefundFailed(Message message) {
        handle("escrow-service.bank-refund-failed", message, "bank.refund_failed", "REFUND_TO_BUYER", false);
    }

    @RabbitListener(queues = RabbitMQConfig.RELEASE_COMPLETED_QUEUE)
    public void onReleaseCompleted(Message message) {
        handle("escrow-service.bank-release-completed", message, "bank.release_completed", "RELEASE_TO_SELLER", true);
    }

    @RabbitListener(queues = RabbitMQConfig.RELEASE_FAILED_QUEUE)
    public void onReleaseFailed(Message message) {
        handle("escrow-service.bank-release-failed", message, "bank.release_failed", "RELEASE_TO_SELLER", false);
    }

    private void handle(String consumer, Message message, String eventType, String entryType, boolean returned) {
        EventEnvelope envelope = parse(message.getBody());
        ReturnResultCommand command = toCommand(envelope, eventType, entryType, returned);
        transactionTemplate.executeWithoutResult(status -> {
            if (!processedEvents.markProcessed(consumer, envelope)) {
                log.info("event=bank_return_result_duplicate eventId={} contractId={}", envelope.eventId(),
                        command.contractId());
                return;
            }
            recordReturnResult.execute(command);
        });
    }

    private EventEnvelope parse(byte[] body) {
        try {
            return codec.parse(body);
        } catch (InvalidEnvelopeException | IllegalArgumentException e) {
            throw new InvalidEventPayloadException("Malformed event envelope");
        }
    }

    ReturnResultCommand toCommand(EventEnvelope envelope, String eventType, String entryType, boolean returned) {
        if (!eventType.equals(envelope.eventType()) || !PRODUCER.equals(envelope.producer())
                || envelope.eventVersion() != 1) {
            throw new InvalidEventPayloadException("Unexpected event type, producer or version");
        }
        Map<String, Object> payload = envelope.payload();
        Set<String> keys = payload.keySet();
        String last = returned ? "ledgerEntryId" : "failureReason";
        if (!keys.containsAll(COMMON) || !keys.contains(last)
                || keys.size() != COMMON.size() + 1 + (keys.contains("milestoneId") ? 1 : 0)) {
            throw new InvalidEventPayloadException("Payload fields do not match " + eventType);
        }
        if (!entryType.equals(payload.get("entryType"))) {
            throw new InvalidEventPayloadException("entryType of " + eventType + " must be " + entryType);
        }
        String eventId = envelope.eventId().toString();
        return new ReturnResultCommand(
                eventId,
                envelope.correlationId() != null ? envelope.correlationId() : eventId,
                uuid(payload, "contractId"),
                uuid(payload, "sourceEventId"),
                uuid(payload, "userId"),
                entryType,
                money(payload),
                returned,
                returned ? uuid(payload, "ledgerEntryId") : null,
                returned ? null : reason(payload));
    }

    private static String uuid(Map<String, Object> payload, String field) {
        if (!(payload.get(field) instanceof String text) || !UUID.matcher(text).matches()) {
            throw new InvalidEventPayloadException(field + " is not a UUID");
        }
        return text.toLowerCase();
    }

    private static BigDecimal money(Map<String, Object> payload) {
        if (!(payload.get("amount") instanceof String text) || !POSITIVE_MONEY.matcher(text).matches()) {
            throw new InvalidEventPayloadException("amount is not a positive money string");
        }
        return new BigDecimal(text);
    }

    private static String reason(Map<String, Object> payload) {
        if (!(payload.get("failureReason") instanceof String text) || text.isEmpty()) {
            throw new InvalidEventPayloadException("failureReason is required");
        }
        return text;
    }
}
