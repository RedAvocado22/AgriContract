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

    /**
     * TODO(#101): check eventType/producer/version, check the payload keys against contractSigned
     *  (required + additionalProperties: false), read the fields escrow needs, decide the correlationId
     *  (contract.signed does not require one; bank.lock_requested does).
     */
    ContractSignedCommand toCommand(EventEnvelope envelope) {
        throw new UnsupportedOperationException("TODO #101");
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
