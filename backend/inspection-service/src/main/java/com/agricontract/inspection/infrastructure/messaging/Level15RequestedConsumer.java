package com.agricontract.inspection.infrastructure.messaging;

import com.agricontract.eventing.*;
import com.agricontract.inspection.application.usecase.ManageLevel15Assignments;
import com.agricontract.inspection.application.dto.Level15AssignmentRequestedCommand;
import com.agricontract.inspection.application.exception.InvalidEventPayloadException;
import com.agricontract.eventing.inbox.ProcessedEventStore;
import com.agricontract.inspection.infrastructure.config.AssignmentRabbitConfiguration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
public class Level15RequestedConsumer {
    private static final String CONSUMER_NAME = "inspection.level1_5_requested";
    private static final Set<String> FIELDS = Set.of("contractId", "milestoneId", "reason",
            "reportDueBusinessDays", "initiatedByActorId", "requestedAt");
    private static final Set<String> REASONS = Set.of("DISPUTE_ROUTED", "MANDATORY_INSPECTION",
            "FORCE_MAJEURE_CONTEST", "DECISION_CONTESTED", "LEVEL2_BUFFER_EXPIRED");
    private final EventEnvelopeCodec codec;
    private final ManageLevel15Assignments assignments;
    private final ProcessedEventStore processedEvents;
    private final TransactionTemplate transactionTemplate;

    @RabbitListener(queues = AssignmentRabbitConfiguration.QUEUE)
    public void accept(Message message) {
        EventEnvelope event;
        Level15AssignmentRequestedCommand command;
        try {
            event = codec.parse(message.getBody());
            if (!"inspection.level1_5_requested".equals(event.eventType())
                    || !"contract-service".equals(event.producer()) || event.eventVersion() != 1)
                throw new IllegalArgumentException("Unexpected assignment event");
            command = toCommand(event);
        } catch (InvalidEnvelopeException | IllegalArgumentException | java.time.DateTimeException ex) {
            throw new InvalidEventPayloadException("Invalid Level 1.5 request; inspect the restricted DLQ");
        }
        // Dependency/database failures remain retryable and roll back the processed-event claim.
        transactionTemplate.executeWithoutResult(status -> {
            if (!processedEvents.markProcessed(CONSUMER_NAME, event)) return;
            assignments.requested(command);
        });
    }

    private Level15AssignmentRequestedCommand toCommand(EventEnvelope event) {
        Map<String, Object> payload = event.payload();
        if (!payload.keySet().equals(FIELDS)
                || !(payload.get("reportDueBusinessDays") instanceof Integer days)
                || days < 1 || days > 30 || !REASONS.contains(text(payload.get("reason"))))
            throw new IllegalArgumentException("Invalid assignment payload");
        java.time.OffsetDateTime.parse(text(payload.get("requestedAt")));
        return new Level15AssignmentRequestedCommand(id(payload.get("contractId")),
                id(payload.get("milestoneId")), days,
                payload.get("initiatedByActorId") == null ? null : id(payload.get("initiatedByActorId")),
                event.eventId().toString());
    }

    private static String text(Object value) {
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException("Invalid field");
        return text;
    }

    private static String id(Object value) {
        String text = text(value);
        String canonical = UUID.fromString(text).toString();
        if (!canonical.equalsIgnoreCase(text)) throw new IllegalArgumentException("Invalid UUID");
        return canonical;
    }
}
