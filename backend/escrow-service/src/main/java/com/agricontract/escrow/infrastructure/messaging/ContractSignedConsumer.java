package com.agricontract.escrow.infrastructure.messaging;

import com.agricontract.escrow.application.dto.ContractSignedCommand;
import com.agricontract.escrow.application.exception.InvalidEventPayloadException;
import com.agricontract.escrow.application.usecase.RequestActivationDepositsUseCase;
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
 * Reads contract.signed (golden-flow-events.yaml contractSigned) and requests the activation deposits.
 * A payload that does not match the schema is an InvalidEventPayloadException: DLQ, no retry.
 * Dedup and the transaction live here, because ProcessedEventStore takes the envelope and the use case only takes
 * a command: the eventId is claimed and the legs + outbox rows are written in one transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractSignedConsumer {

    static final String CONSUMER_NAME = "escrow-service.contract-signed";
    static final String EVENT_TYPE = "contract.signed";
    static final String PRODUCER = "contract-service";

    // contractSigned payload: every field is required and nothing else is allowed (additionalProperties: false).
    private static final Set<String> FIELDS = Set.of(
            "contractId", "listingId", "reservedQuantity", "commodity", "buyerId", "sellerId", "totalAmount",
            "buyerDepositAmount", "sellerDepositAmount", "signedContentHash", "signedAt");

    private static final Pattern UUID =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    // $defs.money: exactly two decimals, zero allowed (sellerDepositAmount is 0.00 without a seller deposit).
    private static final Pattern MONEY = Pattern.compile("^[0-9]+\\.[0-9]{2}$");

    private final EventEnvelopeCodec codec;
    private final ProcessedEventStore processedEvents;
    private final RequestActivationDepositsUseCase requestActivationDeposits;
    private final TransactionTemplate transactionTemplate;

    @RabbitListener(queues = RabbitMQConfig.CONTRACT_SIGNED_QUEUE)
    public void onContractSigned(Message message) {
        EventEnvelope envelope = parse(message.getBody());
        ContractSignedCommand command = toCommand(envelope);

        transactionTemplate.executeWithoutResult(status -> {
            if (!processedEvents.markProcessed(CONSUMER_NAME, envelope)) {
                log.info("event=contract_signed_duplicate eventId={} contractId={}",
                        envelope.eventId(), command.contractId());
                return;
            }
            requestActivationDeposits.execute(command);
        });
    }

    private EventEnvelope parse(byte[] body) {
        try {
            return codec.parse(body);
        } catch (InvalidEnvelopeException | IllegalArgumentException e) {
            throw new InvalidEventPayloadException("Malformed event envelope");
        }
    }

    ContractSignedCommand toCommand(EventEnvelope envelope) {
        if (!EVENT_TYPE.equals(envelope.eventType()) || !PRODUCER.equals(envelope.producer())
                || envelope.eventVersion() != 1) {
            throw new InvalidEventPayloadException("Unexpected event type, producer or version");
        }
        Map<String, Object> payload = envelope.payload();
        if (!payload.keySet().equals(FIELDS)) {
            throw new InvalidEventPayloadException("Payload fields do not match contract.signed");
        }

        String eventId = envelope.eventId().toString();
        // contract.signed may omit correlationId but bank.lock_requested may not: without one, the signed event
        // starts the activation chain (D27). A present correlationId is kept so the chain stays joined to the sign request.
        String correlationId = envelope.correlationId() != null ? envelope.correlationId() : eventId;

        return new ContractSignedCommand(
                eventId,
                correlationId,
                uuid(payload, "contractId"),
                uuid(payload, "buyerId"),
                uuid(payload, "sellerId"),
                money(payload, "buyerDepositAmount"),
                money(payload, "sellerDepositAmount"));
    }

    private static String uuid(Map<String, Object> payload, String field) {
        if (!(payload.get(field) instanceof String text) || !UUID.matcher(text).matches()) {
            throw new InvalidEventPayloadException(field + " is not a UUID");
        }
        return text.toLowerCase();
    }

    private static BigDecimal money(Map<String, Object> payload, String field) {
        if (!(payload.get(field) instanceof String text) || !MONEY.matcher(text).matches()) {
            throw new InvalidEventPayloadException(field + " is not a money string");
        }
        return new BigDecimal(text);
    }
}
