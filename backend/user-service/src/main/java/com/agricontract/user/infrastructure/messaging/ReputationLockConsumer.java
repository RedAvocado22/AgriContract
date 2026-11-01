package com.agricontract.user.infrastructure.messaging;

import com.agricontract.eventing.EventEnvelope;
import com.agricontract.eventing.EventEnvelopeCodec;
import com.agricontract.eventing.InvalidEnvelopeException;
import com.agricontract.eventing.inbox.ProcessedEventStore;
import com.agricontract.user.application.dto.ApplyLockChangeCommand;
import com.agricontract.user.application.dto.LockChangeOutcome;
import com.agricontract.user.application.exception.InvalidEventPayloadException;
import com.agricontract.user.application.usecase.ApplyLockChangeUseCase;
import com.agricontract.user.domain.model.vo.LockAction;
import com.agricontract.user.infrastructure.config.RabbitConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads reputation.locked / reputation.unlocked (golden-flow-events.yaml reputationLocked / reputationUnlocked).
 * Both arrive on one queue, so a single consumer sees them in publish order; correctness still rests on lockRevision.
 * The eventId claim, the projection update and the notification command commit in one transaction: a redelivery
 * is skipped by the claim, an older revision by the UPDATE, so the command is written at most once per decision.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReputationLockConsumer {

    static final String CONSUMER_NAME = "user-service.reputation-lock";
    static final String PRODUCER = "reputation-service";
    static final String LOCKED = "reputation.locked";
    static final String UNLOCKED = "reputation.unlocked";

    // additionalProperties: false and every field required, so the key set must match exactly.
    private static final Set<String> LOCKED_FIELDS =
            Set.of("userId", "lockedUntil", "reasonCode", "lockRevision", "occurredAt");
    private static final Set<String> UNLOCKED_FIELDS =
            Set.of("userId", "effectiveLockedUntil", "reasonCode", "lockRevision", "occurredAt");

    private static final Pattern UUID =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final EventEnvelopeCodec codec;
    private final ProcessedEventStore processedEvents;
    private final ApplyLockChangeUseCase applyLockChange;
    private final TransactionTemplate transactionTemplate;

    @RabbitListener(queues = RabbitConfiguration.REPUTATION_LOCK_QUEUE)
    public void onLockChanged(Message message) {
        EventEnvelope envelope = parse(message.getBody());
        ApplyLockChangeCommand command = toCommand(envelope);

        transactionTemplate.executeWithoutResult(status -> {
            if (!processedEvents.markProcessed(CONSUMER_NAME, envelope)) {
                log.info("event=reputation_lock_duplicate eventId={} userId={}", envelope.eventId(), command.userId());
                return;
            }
            LockChangeOutcome outcome = applyLockChange.execute(command);
            if (outcome == LockChangeOutcome.UNKNOWN_USER) {
                // Acked, not dead-lettered: there is no profile to lock, and a retry would not create one.
                log.warn("event=reputation_lock_unknown_user eventId={} userId={} lockRevision={}",
                        envelope.eventId(), command.userId(), command.lockRevision());
            } else {
                log.info("event=reputation_lock_{} eventId={} userId={} action={} lockRevision={}",
                        outcome.name().toLowerCase(), envelope.eventId(), command.userId(), command.action(),
                        command.lockRevision());
            }
        });
    }

    private EventEnvelope parse(byte[] body) {
        try {
            return codec.parse(body);
        } catch (InvalidEnvelopeException | IllegalArgumentException e) {
            throw new InvalidEventPayloadException("Malformed event envelope");
        }
    }

    ApplyLockChangeCommand toCommand(EventEnvelope envelope) {
        if (!PRODUCER.equals(envelope.producer()) || envelope.eventVersion() != 1) {
            throw new InvalidEventPayloadException("Unexpected producer or version");
        }
        Map<String, Object> payload = envelope.payload();
        LockAction action;
        Instant until;
        if (LOCKED.equals(envelope.eventType())) {
            requireFields(payload, LOCKED_FIELDS);
            action = LockAction.LOCKED;
            until = dateTime(payload, "lockedUntil", false);
        } else if (UNLOCKED.equals(envelope.eventType())) {
            requireFields(payload, UNLOCKED_FIELDS);
            action = LockAction.UNLOCKED;
            until = dateTime(payload, "effectiveLockedUntil", true);
        } else {
            throw new InvalidEventPayloadException("Unexpected event type");
        }
        dateTime(payload, "occurredAt", false);
        if (!(payload.get("reasonCode") instanceof String reasonCode)) {
            throw new InvalidEventPayloadException("reasonCode is not a string");
        }

        String eventId = envelope.eventId().toString();
        // The notification command needs a UUID correlationId; one this event does not carry starts here.
        String correlationId = envelope.correlationId() != null && UUID.matcher(envelope.correlationId()).matches()
                ? envelope.correlationId()
                : eventId;

        return new ApplyLockChangeCommand(eventId, correlationId, uuid(payload, "userId"), action, until, reasonCode,
                revision(payload));
    }

    private static void requireFields(Map<String, Object> payload, Set<String> fields) {
        if (!payload.keySet().equals(fields)) {
            throw new InvalidEventPayloadException("Payload fields do not match the event schema");
        }
    }

    private static String uuid(Map<String, Object> payload, String field) {
        if (!(payload.get(field) instanceof String text) || !UUID.matcher(text).matches()) {
            throw new InvalidEventPayloadException(field + " is not a UUID");
        }
        return text.toLowerCase();
    }

    private static long revision(Map<String, Object> payload) {
        Object value = payload.get("lockRevision");
        // The codec reads JSON integers as Integer or Long; a float or a numeric string is not an integer.
        if (!(value instanceof Integer || value instanceof Long) || ((Number) value).longValue() < 1) {
            throw new InvalidEventPayloadException("lockRevision is not an integer >= 1");
        }
        return ((Number) value).longValue();
    }

    // Truncated to microseconds: locked_until is DATETIME(6), so the stored value is exactly what is compared later.
    private static Instant dateTime(Map<String, Object> payload, String field, boolean nullable) {
        Object value = payload.get(field);
        if (value == null && nullable) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw new InvalidEventPayloadException(field + " is not a date-time");
        }
        try {
            return OffsetDateTime.parse(text).toInstant().truncatedTo(ChronoUnit.MICROS);
        } catch (DateTimeParseException e) {
            throw new InvalidEventPayloadException(field + " is not a date-time");
        }
    }
}
