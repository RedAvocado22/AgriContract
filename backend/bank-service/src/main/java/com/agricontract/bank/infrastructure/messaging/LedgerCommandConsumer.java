package com.agricontract.bank.infrastructure.messaging;

import com.agricontract.bank.application.dto.LedgerCommand;
import com.agricontract.bank.application.dto.LedgerOperation;
import com.agricontract.bank.application.usecase.ProcessLedgerCommandUseCase;
import com.agricontract.bank.domain.model.EntryType;
import com.agricontract.bank.domain.model.FundType;
import com.agricontract.bank.infrastructure.config.RabbitConfiguration;
import com.agricontract.eventing.EventEnvelope;
import com.agricontract.eventing.EventEnvelopeCodec;
import com.agricontract.eventing.InvalidEnvelopeException;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads bank.lock_requested exactly as golden-flow-events.yaml freezes it and hands a LedgerCommand to the use
 * case. Anything that does not match the schema is an InvalidEventPayloadException (DLQ, no retry). A payload that
 * matches the schema but breaks a ledger rule is not rejected here: the use case answers it with bank.lock_failed.
 */
@Component
@RequiredArgsConstructor
public class LedgerCommandConsumer {

    static final String PRODUCER = "escrow-service";

    private static final Set<String> REQUIRED = Set.of("contractId", "userId", "entryType", "fundType",
            "remedyDecisionId", "remedyLegId", "amount", "sourceEventId");
    private static final Set<String> ALLOWED = Set.of("contractId", "milestoneId", "userId", "entryType", "fundType",
            "remedyDecisionId", "remedyLegId", "amount", "sourceEventId");
    private static final Pattern UUID =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    // $defs.positiveMoney: exactly two decimals, no leading zeros, zero excluded.
    private static final Pattern POSITIVE_MONEY =
            Pattern.compile("^(?:0\\.(?:0[1-9]|[1-9][0-9])|[1-9][0-9]*\\.[0-9]{2})$");

    private final EventEnvelopeCodec codec;
    private final ProcessLedgerCommandUseCase processLedgerCommand;

    @RabbitListener(queues = RabbitConfiguration.LOCK_REQUEST_QUEUE)
    public void onLockRequested(Message message) {
        processLedgerCommand.execute(toCommand(message.getBody(), LedgerOperation.LOCK));
    }

    /** Seller deposit or milestone payment to the seller (#124, D41 activation refund). */
    @RabbitListener(queues = RabbitConfiguration.RELEASE_REQUEST_QUEUE)
    public void onReleaseRequested(Message message) {
        processLedgerCommand.execute(toCommand(message.getBody(), LedgerOperation.RELEASE));
    }

    /** Buyer deposit or milestone payment back to the buyer (#138 refund leg, D41 activation refund). */
    @RabbitListener(queues = RabbitConfiguration.REFUND_REQUEST_QUEUE)
    public void onRefundRequested(Message message) {
        processLedgerCommand.execute(toCommand(message.getBody(), LedgerOperation.REFUND));
    }

    /** The command of one queue: its eventType and entryType must be that queue's operation. */
    LedgerCommand toCommand(byte[] body, LedgerOperation operation) {
        EventEnvelope envelope;
        try {
            envelope = codec.parse(body);
        } catch (InvalidEnvelopeException | IllegalArgumentException e) {
            throw new InvalidEventPayloadException("Malformed event envelope");
        }
        if (!operation.requested().equals(envelope.eventType()) || !PRODUCER.equals(envelope.producer())
                || envelope.eventVersion() != 1) {
            throw new InvalidEventPayloadException("Unexpected event type, producer or version");
        }
        if (envelope.correlationId() == null) {
            throw new InvalidEventPayloadException("correlationId is required for bank commands");
        }

        Map<String, Object> payload = envelope.payload();
        if (!payload.keySet().containsAll(REQUIRED) || !ALLOWED.containsAll(payload.keySet())) {
            throw new InvalidEventPayloadException("Payload fields do not match " + operation.requested());
        }
        String sourceEventId = uuid(payload, "sourceEventId");
        // The request eventId is the idempotency key; the payload copy must be the same value (bank design §4).
        if (!sourceEventId.equalsIgnoreCase(envelope.eventId().toString())) {
            throw new InvalidEventPayloadException("sourceEventId must equal the envelope eventId");
        }
        EntryType entryType = enumValue(EntryType.class, payload, "entryType");
        if (!operation.allows(entryType)) {
            throw new InvalidEventPayloadException("entryType " + entryType + " is not allowed in " + operation.requested());
        }

        return new LedgerCommand(
                envelope.eventId().toString(),
                envelope.correlationId(),
                uuid(payload, "contractId"),
                nullableUuid(payload, "milestoneId"),
                uuid(payload, "userId"),
                entryType,
                enumValue(FundType.class, payload, "fundType"),
                nullableUuid(payload, "remedyDecisionId"),
                nullableUuid(payload, "remedyLegId"),
                money(payload, "amount"),
                sourceEventId);
    }

    private static String uuid(Map<String, Object> payload, String field) {
        String value = nullableUuid(payload, field);
        if (value == null) {
            throw new InvalidEventPayloadException(field + " is required");
        }
        return value;
    }

    private static String nullableUuid(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text) || !UUID.matcher(text).matches()) {
            throw new InvalidEventPayloadException(field + " is not a UUID");
        }
        return text.toLowerCase();
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, Map<String, Object> payload, String field) {
        if (!(payload.get(field) instanceof String text)) {
            throw new InvalidEventPayloadException(field + " is required");
        }
        try {
            return Enum.valueOf(type, text);
        } catch (IllegalArgumentException e) {
            throw new InvalidEventPayloadException(field + " has an unknown value");
        }
    }

    private static BigDecimal money(Map<String, Object> payload, String field) {
        if (!(payload.get(field) instanceof String text) || !POSITIVE_MONEY.matcher(text).matches()) {
            throw new InvalidEventPayloadException(field + " is not a positive money string");
        }
        return new BigDecimal(text);
    }
}
