package com.agricontract.reputation.infrastructure.messaging;

import com.agricontract.reputation.application.exception.ImmutableFactConflictException;
import com.agricontract.reputation.application.usecase.AppendReputationFact;
import com.agricontract.reputation.application.dto.AppendCompletedContractCommand;
import com.agricontract.reputation.application.dto.AppendDisputeOutcomeCommand;
import com.agricontract.eventing.EventEnvelope;
import com.agricontract.eventing.EventEnvelopeCodec;
import com.agricontract.eventing.InvalidEnvelopeException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ReputationFactConsumer {
    private final EventEnvelopeCodec codec;
    private final AppendReputationFact append;
    public ReputationFactConsumer(EventEnvelopeCodec codec, AppendReputationFact append) {
        this.codec = codec;
        this.append = append;
    }
    @RabbitListener(queues = "reputation-svc.contract.settled")
    public void completed(Message message) { accept(message, "contract.settled"); }

    @RabbitListener(queues = "reputation-svc.milestone.dispute_resolved")
    public void dispute(Message message) { accept(message, "milestone.dispute_resolved"); }

    private void accept(Message message, String expectedType) {
        try {
            EventEnvelope envelope = codec.parse(message.getBody());
            if (!expectedType.equals(envelope.eventType()) || !"contract-service".equals(envelope.producer()))
                throw new IllegalArgumentException("Unexpected fact event");
            if (envelope.eventVersion() != 1)
                throw new IllegalArgumentException("Unsupported event version");
            UUID source = envelope.eventId();
            Instant occurred = envelope.occurredAt();
            Map<String, Object> payload = envelope.payload();
            if (expectedType.equals("contract.settled")) {
                fields(payload, Set.of("contractId", "buyerId", "sellerId"));
                append.execute(new AppendCompletedContractCommand(id(payload, "contractId"), id(payload, "buyerId"),
                        id(payload, "sellerId"), occurred, source));
            } else {
                fields(payload, Set.of("contractId", "milestoneId", "flaggedBy", "flaggedByUserId", "resolutionFavors"));
                if (!"BUYER".equals(text(payload, "flaggedBy"))) throw new IllegalArgumentException("Invalid flagger role");
                append.execute(new AppendDisputeOutcomeCommand(id(payload, "milestoneId"), id(payload, "contractId"),
                        id(payload, "flaggedByUserId"), text(payload, "resolutionFavors"),
                        occurred, source));
            }
        } catch (InvalidEnvelopeException | IllegalArgumentException | java.time.DateTimeException | ImmutableFactConflictException e) {
            // Do not include raw payload/PII or credentials in exception messages or logs.
            throw new AmqpRejectAndDontRequeueException("Invalid or conflicting reputation fact");
        }
    }
    private static void fields(Map<String, Object> payload, Set<String> allowed) {
        payload.keySet().forEach(name -> {
            if (!allowed.contains(name)) throw new IllegalArgumentException("Unexpected field");
        });
    }
    private static String text(Map<String, Object> payload, String name) {
        if (!(payload.get(name) instanceof String value) || value.isBlank())
            throw new IllegalArgumentException("Missing field");
        return value;
    }
    private static UUID id(Map<String, Object> payload, String name) {
        String value = text(payload, name);
        UUID id = UUID.fromString(value);
        if (!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException("Invalid UUID");
        return id;
    }
}
