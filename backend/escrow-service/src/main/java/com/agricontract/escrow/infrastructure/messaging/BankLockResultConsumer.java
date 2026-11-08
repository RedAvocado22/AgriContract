package com.agricontract.escrow.infrastructure.messaging;

import com.agricontract.escrow.application.dto.LockResultCommand;
import com.agricontract.escrow.application.exception.InvalidEventPayloadException;
import com.agricontract.escrow.application.usecase.RecordLockResultUseCase;
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
 * bank.lock_completed and bank.lock_failed (golden-flow-events.yaml bankLockCompleted / bankLockFailed) for deposit
 * legs (#102). A payload outside the schema is an InvalidEventPayloadException: DLQ, no retry. The eventId is
 * claimed and the leg updated (plus escrow.deposit_locked when it completes the set) in one transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BankLockResultConsumer {

    static final String COMPLETED_CONSUMER = "escrow-service.bank-lock-completed";
    static final String FAILED_CONSUMER = "escrow-service.bank-lock-failed";
    static final String COMPLETED = "bank.lock_completed";
    static final String FAILED = "bank.lock_failed";
    static final String PRODUCER = "bank-service";

    private static final Set<String> COMMON = Set.of("contractId", "userId", "entryType", "fundType",
            "remedyDecisionId", "remedyLegId", "amount", "sourceEventId");
    private static final Set<String> ENTRY_TYPES = Set.of("LOCK_BUYER_DEPOSIT", "LOCK_SELLER_DEPOSIT", "LOCK_MILESTONE");
    private static final Pattern UUID =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    // $defs.positiveMoney: two decimals, strictly positive.
    private static final Pattern POSITIVE_MONEY =
            Pattern.compile("^(?:0\\.(?:0[1-9]|[1-9][0-9])|[1-9][0-9]*\\.[0-9]{2})$");

    private final EventEnvelopeCodec codec;
    private final ProcessedEventStore processedEvents;
    private final RecordLockResultUseCase recordLockResult;
    private final TransactionTemplate transactionTemplate;

    @RabbitListener(queues = RabbitMQConfig.LOCK_COMPLETED_QUEUE)
    public void onLockCompleted(Message message) {
        handle(COMPLETED_CONSUMER, parse(message.getBody()), true);
    }

    @RabbitListener(queues = RabbitMQConfig.LOCK_FAILED_QUEUE)
    public void onLockFailed(Message message) {
        handle(FAILED_CONSUMER, parse(message.getBody()), false);
    }

    private void handle(String consumer, EventEnvelope envelope, boolean locked) {
        LockResultCommand command = toCommand(envelope, locked);
        transactionTemplate.executeWithoutResult(status -> {
            if (!processedEvents.markProcessed(consumer, envelope)) {
                log.info("event=bank_lock_result_duplicate eventId={} contractId={}",
                        envelope.eventId(), command.contractId());
                return;
            }
            recordLockResult.execute(command);
        });
    }

    private EventEnvelope parse(byte[] body) {
        try {
            return codec.parse(body);
        } catch (InvalidEnvelopeException | IllegalArgumentException e) {
            throw new InvalidEventPayloadException("Malformed event envelope");
        }
    }

    LockResultCommand toCommand(EventEnvelope envelope, boolean locked) {
        String expectedType = locked ? COMPLETED : FAILED;
        if (!expectedType.equals(envelope.eventType()) || !PRODUCER.equals(envelope.producer())
                || envelope.eventVersion() != 1) {
            throw new InvalidEventPayloadException("Unexpected event type, producer or version");
        }
        Map<String, Object> payload = envelope.payload();
        Set<String> keys = payload.keySet();
        String last = locked ? "ledgerEntryId" : "failureReason";
        // Required fields all present; milestoneId is the only optional one; nothing else (additionalProperties).
        if (!keys.containsAll(COMMON) || !keys.contains(last)
                || keys.size() != COMMON.size() + 1 + (keys.contains("milestoneId") ? 1 : 0)) {
            throw new InvalidEventPayloadException("Payload fields do not match " + expectedType);
        }
        if (!(payload.get("entryType") instanceof String entryType) || !ENTRY_TYPES.contains(entryType)) {
            throw new InvalidEventPayloadException("entryType is not a lock entry type");
        }
        String eventId = envelope.eventId().toString();
        return new LockResultCommand(
                eventId,
                envelope.correlationId() != null ? envelope.correlationId() : eventId,
                uuid(payload, "contractId"),
                uuid(payload, "sourceEventId"),
                uuid(payload, "userId"),
                entryType,
                money(payload),
                locked,
                locked ? uuid(payload, "ledgerEntryId") : null,
                locked ? null : reason(payload));
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
