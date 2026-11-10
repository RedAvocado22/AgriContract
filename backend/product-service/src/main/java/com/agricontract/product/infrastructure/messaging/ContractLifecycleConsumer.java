package com.agricontract.product.infrastructure.messaging;

import com.agricontract.eventing.EventEnvelope;
import com.agricontract.eventing.EventEnvelopeCodec;
import com.agricontract.eventing.InvalidEnvelopeException;
import com.agricontract.product.application.dto.ContractLifecycleCommand;
import com.agricontract.product.application.exception.InvalidEventPayloadException;
import com.agricontract.product.application.usecase.ApplyContractLifecycleEventUseCase;
import com.agricontract.product.domain.model.vo.ContractLifecycleEventType;
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
 * Consumes contract.signed, contract.activated, contract.activation_failed and contract.withdrawn
 * (contracts/events/golden-flow-events.yaml), one queue each. The envelope is validated by the shared
 * EventEnvelopeCodec; here only the fields inventory needs are read, and the payload is checked against the frozen
 * closed field set. A malformed event goes straight to the DLQ (not retried); processing failures are retried, then
 * dead-lettered.
 */
@Slf4j
@Component
public class ContractLifecycleConsumer {

    private static final Set<String> SIGNED_FIELDS = Set.of("contractId", "listingId", "reservedQuantity",
            "commodity", "buyerId", "sellerId", "totalAmount", "buyerDepositAmount", "sellerDepositAmount",
            "signedContentHash", "signedAt");

    private final EventEnvelopeCodec codec;
    private final ApplyContractLifecycleEventUseCase useCase;

    public ContractLifecycleConsumer(EventEnvelopeCodec codec, ApplyContractLifecycleEventUseCase useCase) {
        this.codec = codec;
        this.useCase = useCase;
    }

    @RabbitListener(queues = RabbitMQConfig.CONTRACT_SIGNED_QUEUE)
    public void onContractSigned(Message message) {
        useCase.execute(parse(message, ContractLifecycleEventType.CONTRACT_SIGNED, SIGNED_FIELDS, "signedAt"));
    }

    @RabbitListener(queues = RabbitMQConfig.CONTRACT_ACTIVATED_QUEUE)
    public void onContractActivated(Message message) {
        useCase.execute(parse(message, ContractLifecycleEventType.CONTRACT_ACTIVATED,
                inventoryFields("activatedAt"), "activatedAt"));
    }

    @RabbitListener(queues = RabbitMQConfig.CONTRACT_ACTIVATION_FAILED_QUEUE)
    public void onContractActivationFailed(Message message) {
        useCase.execute(parse(message, ContractLifecycleEventType.CONTRACT_ACTIVATION_FAILED,
                inventoryFields("activationFailedAt"), "activationFailedAt"));
    }

    @RabbitListener(queues = RabbitMQConfig.CONTRACT_WITHDRAWN_QUEUE)
    public void onContractWithdrawn(Message message) {
        useCase.execute(parse(message, ContractLifecycleEventType.CONTRACT_WITHDRAWN,
                inventoryFields("withdrawnAt"), "withdrawnAt"));
    }

    private static Set<String> inventoryFields(String timestampField) {
        return Set.of("contractId", "listingId", "reservedQuantity", timestampField);
    }

    private ContractLifecycleCommand parse(Message message, ContractLifecycleEventType expected, Set<String> fields,
                                           String timestampField) {
        try {
            EventEnvelope event = codec.parse(message.getBody());
            if (!expected.wireName().equals(event.eventType())
                    || !"contract-service".equals(event.producer())
                    || event.eventVersion() != 1) {
                throw new IllegalArgumentException("Unexpected event type, producer or version");
            }
            Map<String, Object> payload = event.payload();
            if (payload == null) {
                throw new IllegalArgumentException("Missing payload");
            }
            // the frozen payloads are closed (additionalProperties: false)
            if (!fields.containsAll(payload.keySet())) {
                throw new IllegalArgumentException("Unknown payload field");
            }
            BigDecimal reservedQuantity = quantity(payload.get("reservedQuantity"));
            OffsetDateTime.parse(text(payload, timestampField));
            return new ContractLifecycleCommand(
                    event.eventId(),
                    expected,
                    uuidText(payload, "contractId"),
                    uuidText(payload, "listingId"),
                    reservedQuantity);
        } catch (InvalidEnvelopeException | IllegalArgumentException | DateTimeException e) {
            log.error("event={}_rejected alert=OPERATOR reason={}", expected.wireName().replace('.', '_'),
                    e.getMessage());
            throw new InvalidEventPayloadException("Invalid " + expected.wireName() + " event", e);
        }
    }
}
