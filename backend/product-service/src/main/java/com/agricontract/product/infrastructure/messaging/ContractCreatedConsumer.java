package com.agricontract.product.infrastructure.messaging;

import com.agricontract.eventing.EventEnvelope;
import com.agricontract.eventing.EventEnvelopeCodec;
import com.agricontract.eventing.InvalidEnvelopeException;
import com.agricontract.product.application.exception.InvalidEventPayloadException;
import com.agricontract.product.application.dto.ContractCreatedCommand;
import com.agricontract.product.application.usecase.ConfirmContractCreatedUseCase;
import com.agricontract.product.infrastructure.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

import static com.agricontract.product.infrastructure.messaging.ContractEventPayloads.quantity;
import static com.agricontract.product.infrastructure.messaging.ContractEventPayloads.text;
import static com.agricontract.product.infrastructure.messaging.ContractEventPayloads.uuidText;

/**
 * Consumes contract.created (contracts/events/golden-flow-events.yaml#contractCreated). The envelope is validated by
 * the shared EventEnvelopeCodec; only the contract.created payload is checked here. A malformed event is rejected
 * straight to the DLQ (not retried, see RabbitMQConfig); processing failures are retried, then dead-lettered.
 */
@Slf4j
@Component
public class ContractCreatedConsumer {

    // the frozen payload is closed (additionalProperties: false)
    private static final Set<String> PAYLOAD_FIELDS = Set.of("contractId", "listingId", "reservedQuantity", "createdAt");

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
            if (!PAYLOAD_FIELDS.containsAll(payload.keySet())) {
                throw new IllegalArgumentException("Unknown payload field");
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
            throw new InvalidEventPayloadException("Invalid contract.created event", e);
        }
    }
}
