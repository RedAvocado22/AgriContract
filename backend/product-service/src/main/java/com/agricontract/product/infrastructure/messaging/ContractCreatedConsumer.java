package com.agricontract.product.infrastructure.messaging;

import com.agricontract.product.application.dto.ContractCreatedCommand;
import com.agricontract.product.application.usecase.ConfirmContractCreatedUseCase;
import com.agricontract.product.infrastructure.config.RabbitMQConfig;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Consumes contract.created (contracts/events/golden-flow-events.yaml#contractCreated). A malformed event is
 * rejected straight to the DLQ; processing failures are retried by the listener container, then dead-lettered.
 */
@Slf4j
@Component
public class ContractCreatedConsumer {

    private final ObjectReader json;
    private final ConfirmContractCreatedUseCase confirmContractCreatedUseCase;

    public ContractCreatedConsumer(ObjectMapper objectMapper, ConfirmContractCreatedUseCase confirmContractCreatedUseCase) {
        // BigDecimal so reservedQuantity compares exactly against the stored reservation
        this.json = objectMapper.reader()
                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.confirmContractCreatedUseCase = confirmContractCreatedUseCase;
    }

    @RabbitListener(queues = RabbitMQConfig.CONTRACT_CREATED_QUEUE)
    public void onContractCreated(Message message) {
        confirmContractCreatedUseCase.execute(parse(message));
    }

    private ContractCreatedCommand parse(Message message) {
        try {
            JsonNode event = json.readTree(message.getBody());
            if (!"contract.created".equals(text(event, "eventType"))
                    || !"contract-service".equals(text(event, "producer"))
                    || event.path("eventVersion").asInt() != 1) {
                throw new IllegalArgumentException("Unexpected event type, producer or version");
            }
            JsonNode payload = event.path("payload");
            BigDecimal reservedQuantity = payload.path("reservedQuantity").isNumber()
                    ? payload.get("reservedQuantity").decimalValue() : null;
            if (reservedQuantity == null || reservedQuantity.signum() <= 0) {
                throw new IllegalArgumentException("reservedQuantity must be a positive number");
            }
            OffsetDateTime.parse(text(payload, "createdAt"));
            return new ContractCreatedCommand(
                    UUID.fromString(text(event, "eventId")),
                    uuidText(payload, "contractId"),
                    uuidText(payload, "listingId"),
                    reservedQuantity);
        } catch (IOException | IllegalArgumentException | DateTimeException e) {
            log.error("event=contract_created_rejected alert=OPERATOR reason={}", e.getMessage());
            throw new AmqpRejectAndDontRequeueException("Invalid contract.created event", e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Missing " + field);
        }
        return value.asText();
    }

    // validated as a UUID but kept verbatim, so it matches the id the reserve command stored
    private static String uuidText(JsonNode node, String field) {
        String value = text(node, field);
        UUID.fromString(value);
        return value;
    }
}
