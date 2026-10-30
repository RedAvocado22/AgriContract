package com.agricontract.product.infrastructure.messaging;

import com.agricontract.eventing.EventEnvelope;
import com.agricontract.eventing.EventEnvelopeCodec;
import com.agricontract.eventing.InvalidEnvelopeException;
import com.agricontract.product.application.dto.ContractCreatedCommand;
import com.agricontract.product.application.usecase.ConfirmContractCreatedUseCase;
import com.agricontract.product.infrastructure.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Consumes contract.created (contracts/events/golden-flow-events.yaml#contractCreated). The envelope is validated by
 * the shared EventEnvelopeCodec; only the contract.created payload is checked here. A malformed event is rejected
 * straight to the DLQ (not retried, see RabbitMQConfig); processing failures are retried, then dead-lettered.
 */
@Slf4j
@Component
public class ContractCreatedConsumer {

    private final EventEnvelopeCodec codec;
    private final ConfirmContractCreatedUseCase confirmContractCreatedUseCase;

    public ContractCreatedConsumer(EventEnvelopeCodec codec, ConfirmContractCreatedUseCase confirmContractCreatedUseCase) {
        this.codec = codec;
        this.confirmContractCreatedUseCase = confirmContractCreatedUseCase;
    }

    @RabbitListener(queues = RabbitMQConfig.CONTRACT_CREATED_QUEUE)
    public void onContractCreated(Message message) {
        confirmContractCreatedUseCase.execute(parse(message));
    }

    private ContractCreatedCommand parse(Message message) {
        try {
            EventEnvelope event = codec.parse(message.getBody());
            if (!"contract.created".equals(event.eventType())
                    || !"contract-service".equals(event.producer())
                    || event.eventVersion() != 1) {
                throw new IllegalArgumentException("Unexpected event type, producer or version");
            }
            Map<String, Object> payload = event.payload();
            if (payload == null) {
                throw new IllegalArgumentException("Missing payload");
            }
            BigDecimal reservedQuantity = quantity(payload.get("reservedQuantity"));
            OffsetDateTime.parse(text(payload, "createdAt"));
            return new ContractCreatedCommand(
                    event.eventId(),
                    uuidText(payload, "contractId"),
                    uuidText(payload, "listingId"),
                    reservedQuantity);
        } catch (InvalidEnvelopeException | IllegalArgumentException | DateTimeException e) {
            log.error("event=contract_created_rejected alert=OPERATOR reason={}", e.getMessage());
            throw new AmqpRejectAndDontRequeueException("Invalid contract.created event", e);
        }
    }

    // The codec reads decimals as Double. Going through toString keeps the shortest decimal form, which is exact for
    // every value reserved_quantity DECIMAL(15,3) can hold; new BigDecimal(double) would not be.
    private static BigDecimal quantity(Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("reservedQuantity must be a positive number");
        }
        BigDecimal quantity = new BigDecimal(number.toString());
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("reservedQuantity must be a positive number");
        }
        return quantity;
    }

    private static String text(Map<String, Object> payload, String field) {
        if (!(payload.get(field) instanceof String value) || value.isBlank()) {
            throw new IllegalArgumentException("Missing " + field);
        }
        return value;
    }

    // validated as a UUID but kept verbatim, so it matches the id the reserve command stored
    private static String uuidText(Map<String, Object> payload, String field) {
        String value = text(payload, field);
        UUID.fromString(value);
        return value;
    }
}
