package com.agricontract.reputation.infrastructure.messaging;

import com.agricontract.reputation.application.exception.ImmutableFactConflictException;
import com.agricontract.reputation.application.usecase.AppendReputationFact;
import com.agricontract.reputation.application.dto.AppendCompletedContractCommand;
import com.agricontract.reputation.application.dto.AppendDisputeOutcomeCommand;
import java.io.IOException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.DeserializationFeature;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ReputationFactConsumer {
    private static final Set<String> ENVELOPE = Set.of("eventId", "eventType", "eventVersion", "occurredAt",
            "producer", "aggregateId", "payload", "correlationId", "causationId", "subject");
    private final ObjectReader json;
    private final AppendReputationFact append;
    public ReputationFactConsumer(ObjectMapper json, AppendReputationFact append) {
        this.json = json.reader().with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.append = append;
    }
    @RabbitListener(queues = "reputation-svc.contract.settled")
    public void completed(Message message) { accept(message, "contract.settled"); }

    @RabbitListener(queues = "reputation-svc.milestone.dispute_resolved")
    public void dispute(Message message) { accept(message, "milestone.dispute_resolved"); }

    // These are two service-owned adapters, not a replacement for #89's shared event infrastructure.
    private void accept(Message message, String expectedType) {
        try {
            JsonNode event = json.readTree(message.getBody());
            fields(event, ENVELOPE);
            if (!expectedType.equals(text(event, "eventType")) || !"contract-service".equals(text(event, "producer")))
                throw new IllegalArgumentException("Unexpected fact event");
            if (!event.path("eventVersion").isIntegralNumber() || !event.path("eventVersion").canConvertToInt()
                    || event.path("eventVersion").intValue() != 1)
                throw new IllegalArgumentException("Unsupported event version");
            text(event, "aggregateId");
            optionalText(event, "correlationId"); optionalText(event, "causationId");
            if (event.has("subject")) {
                JsonNode subject = event.get("subject");
                fields(subject, Set.of("type", "id"));
                if (!Set.of("CONTRACT", "USER_PAIR", "SYSTEM").contains(text(subject, "type")))
                    throw new IllegalArgumentException("Invalid subject");
                text(subject, "id");
            }
            UUID source = id(event, "eventId");
            Instant occurred = OffsetDateTime.parse(text(event, "occurredAt")).toInstant();
            JsonNode payload = event.path("payload");
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
        } catch (IOException | IllegalArgumentException | java.time.DateTimeException | ImmutableFactConflictException e) {
            // Do not include raw payload/PII or credentials in exception messages or logs.
            throw new AmqpRejectAndDontRequeueException("Invalid or conflicting reputation fact");
        }
    }
    private static void fields(JsonNode node, Set<String> allowed) {
        if (node == null || !node.isObject()) throw new IllegalArgumentException("Expected object");
        node.fieldNames().forEachRemaining(name -> {
            if (!allowed.contains(name)) throw new IllegalArgumentException("Unexpected field");
        });
    }
    private static String text(JsonNode node, String name) {
        JsonNode value = node.path(name);
        if (!value.isTextual() || value.textValue().isBlank()) throw new IllegalArgumentException("Missing field");
        return value.textValue();
    }
    private static void optionalText(JsonNode node, String name) {
        if (node.hasNonNull(name)) text(node, name);
    }
    private static UUID id(JsonNode node, String name) {
        String value = text(node, name);
        UUID id = UUID.fromString(value);
        if (!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException("Invalid UUID");
        return id;
    }
}
