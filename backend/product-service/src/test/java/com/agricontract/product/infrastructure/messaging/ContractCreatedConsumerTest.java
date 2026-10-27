package com.agricontract.product.infrastructure.messaging;

import com.agricontract.product.application.dto.ContractCreatedCommand;
import com.agricontract.product.application.usecase.ConfirmContractCreatedUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ContractCreatedConsumerTest {

    private static final String EVENT_ID = "5b0d7f8e-2a5c-4e7b-9a43-0c1f6e9d2b11";
    private static final String CONTRACT_ID = "8f14e45f-ceea-467a-9575-6b4a8a0f3c21";
    private static final String LISTING_ID = "c4ca4238-a0b9-4382-8dcc-509a6f75849b";

    private final ConfirmContractCreatedUseCase useCase = mock(ConfirmContractCreatedUseCase.class);
    private final ContractCreatedConsumer consumer = new ContractCreatedConsumer(new ObjectMapper(), useCase);

    @Test
    void validEvent_isHandedToUseCaseWithExactQuantity() {
        consumer.onContractCreated(message(event("contract.created", "40.125")));

        verify(useCase).execute(new ContractCreatedCommand(
                UUID.fromString(EVENT_ID), CONTRACT_ID, LISTING_ID, new BigDecimal("40.125")));
    }

    @Test
    void otherEventType_isRejectedToDlq() {
        assertRejected(event("contract.signed", "40"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "\"40\""})
    void nonPositiveOrNonNumericQuantity_isRejectedToDlq(String quantity) {
        assertRejected(event("contract.created", quantity));
    }

    @Test
    void nonUuidContractId_isRejectedToDlq() {
        assertRejected(event("contract.created", "40").replace(CONTRACT_ID, "contract-1"));
    }

    @Test
    void malformedJson_isRejectedToDlq() {
        assertRejected("{\"eventId\":");
    }

    private void assertRejected(String body) {
        assertThatThrownBy(() -> consumer.onContractCreated(message(body)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verify(useCase, never()).execute(any());
    }

    private static String event(String eventType, String quantity) {
        return """
                {"eventId":"%s","eventType":"%s","eventVersion":1,"occurredAt":"2026-10-27T03:00:00Z",
                 "producer":"contract-service","aggregateId":"%s",
                 "payload":{"contractId":"%s","listingId":"%s","reservedQuantity":%s,
                            "createdAt":"2026-10-27T03:00:00Z"}}
                """.formatted(EVENT_ID, eventType, CONTRACT_ID, CONTRACT_ID, LISTING_ID, quantity);
    }

    private static Message message(String body) {
        return new Message(body.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }
}
