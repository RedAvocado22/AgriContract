package com.agricontract.product.infrastructure.messaging;

import com.agricontract.eventing.EventEnvelopeCodec;
import com.agricontract.product.application.dto.ContractLifecycleCommand;
import com.agricontract.product.application.exception.InvalidEventPayloadException;
import com.agricontract.product.application.usecase.ApplyContractLifecycleEventUseCase;
import com.agricontract.product.domain.model.vo.ContractLifecycleEventType;
import com.agricontract.product.infrastructure.contract.FrozenSchemas;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ContractLifecycleConsumerTest {

    private static final String EVENT_ID = "5b0d7f8e-2a5c-4e7b-9a43-0c1f6e9d2b11";
    private static final String CONTRACT_ID = "8f14e45f-ceea-467a-9575-6b4a8a0f3c21";
    private static final String LISTING_ID = "c4ca4238-a0b9-4382-8dcc-509a6f75849b";
    private static final String BUYER_ID = "a87ff679-a2f3-471d-8181-a67b7542122c";
    private static final String SELLER_ID = "e4da3b7f-bbce-4345-9777-2b0674a318d5";

    private final ApplyContractLifecycleEventUseCase useCase = mock(ApplyContractLifecycleEventUseCase.class);
    private final ContractLifecycleConsumer consumer = new ContractLifecycleConsumer(new EventEnvelopeCodec(), useCase);

    @Test
    void signed_isHandedToUseCaseWithExactQuantity() {
        consumer.onContractSigned(message(signed("40.125")));

        verify(useCase).execute(command(ContractLifecycleEventType.CONTRACT_SIGNED, "40.125"));
    }

    @Test
    void activated_isHandedToUseCase() {
        consumer.onContractActivated(message(inventory("contract.activated", "activatedAt", "40")));

        verify(useCase).execute(command(ContractLifecycleEventType.CONTRACT_ACTIVATED, "40"));
    }

    @Test
    void activationFailed_isHandedToUseCase() {
        consumer.onContractActivationFailed(message(inventory("contract.activation_failed", "activationFailedAt",
                "40")));

        verify(useCase).execute(command(ContractLifecycleEventType.CONTRACT_ACTIVATION_FAILED, "40"));
    }

    @Test
    void withdrawn_isHandedToUseCase() {
        consumer.onContractWithdrawn(message(inventory("contract.withdrawn", "withdrawnAt", "40")));

        verify(useCase).execute(command(ContractLifecycleEventType.CONTRACT_WITHDRAWN, "40"));
    }

    // the codec reads decimals as Double; the command must still carry the exact decimal
    @ParameterizedTest
    @ValueSource(strings = {"0.1", "12.345", "10.50", "999999999999.999"})
    void decimalQuantity_isHandedToUseCaseExactly(String quantity) {
        consumer.onContractWithdrawn(message(inventory("contract.withdrawn", "withdrawnAt", quantity)));

        verify(useCase).execute(argThat(c -> c.reservedQuantity().compareTo(new BigDecimal(quantity)) == 0));
    }

    // fixtures every accepted-path test uses must stay valid against the frozen contract
    @Test
    void fixtures_matchFrozenSchemas() throws IOException {
        ObjectMapper json = new ObjectMapper();
        FrozenSchemas.assertEvent("contractSigned", json.readTree(signed("40.125")));
        FrozenSchemas.assertEvent("contractActivated",
                json.readTree(inventory("contract.activated", "activatedAt", "40")));
        FrozenSchemas.assertEvent("contractActivationFailed",
                json.readTree(inventory("contract.activation_failed", "activationFailedAt", "40")));
        FrozenSchemas.assertEvent("contractWithdrawn",
                json.readTree(inventory("contract.withdrawn", "withdrawnAt", "40")));
    }

    @Test
    void eventOfAnotherTypeOnTheQueue_isRejectedToDlq() {
        assertRejected(consumer::onContractActivated, inventory("contract.withdrawn", "activatedAt", "40"));
        assertRejected(consumer::onContractWithdrawn, inventory("contract.activated", "withdrawnAt", "40"));
    }

    @Test
    void otherProducerOrVersion_isRejectedToDlq() {
        String withdrawn = inventory("contract.withdrawn", "withdrawnAt", "40");
        assertRejected(consumer::onContractWithdrawn,
                withdrawn.replace("\"contract-service\"", "\"escrow-service\""));
        assertRejected(consumer::onContractWithdrawn, withdrawn.replace("\"eventVersion\":1", "\"eventVersion\":2"));
    }

    @Test
    void unknownPayloadField_isRejectedToDlq() {
        assertRejected(consumer::onContractSigned, signed("40").replace("\"commodity\"", "\"extra\":1,\"commodity\""));
        // another event's timestamp is not part of this payload
        assertRejected(consumer::onContractActivated, inventory("contract.activated", "withdrawnAt", "40"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "\"40\""})
    void nonPositiveOrNonNumericQuantity_isRejectedToDlq(String quantity) {
        assertRejected(consumer::onContractActivationFailed,
                inventory("contract.activation_failed", "activationFailedAt", quantity));
    }

    @Test
    void nonUuidContractIdOrBadTimestamp_isRejectedToDlq() {
        String withdrawn = inventory("contract.withdrawn", "withdrawnAt", "40");
        assertRejected(consumer::onContractWithdrawn, withdrawn.replace("\"" + CONTRACT_ID + "\"", "\"contract-1\""));
        assertRejected(consumer::onContractWithdrawn,
                withdrawn.replace("\"withdrawnAt\":\"2026-11-10T03:00:00Z\"", "\"withdrawnAt\":\"yesterday\""));
    }

    private void assertRejected(Consumer<Message> listener, String body) {
        assertThatThrownBy(() -> listener.accept(message(body)))
                .isInstanceOf(InvalidEventPayloadException.class);
        verify(useCase, never()).execute(any());
    }

    private static ContractLifecycleCommand command(ContractLifecycleEventType type, String quantity) {
        return new ContractLifecycleCommand(UUID.fromString(EVENT_ID), type, CONTRACT_ID, LISTING_ID,
                new BigDecimal(quantity));
    }

    private static String inventory(String eventType, String timestampField, String quantity) {
        return """
                {"eventId":"%s","eventType":"%s","eventVersion":1,"occurredAt":"2026-11-10T03:00:00Z",
                 "producer":"contract-service","aggregateId":"%s",
                 "payload":{"contractId":"%s","listingId":"%s","reservedQuantity":%s,
                            "%s":"2026-11-10T03:00:00Z"}}
                """.formatted(EVENT_ID, eventType, CONTRACT_ID, CONTRACT_ID, LISTING_ID, quantity, timestampField);
    }

    private static String signed(String quantity) {
        return """
                {"eventId":"%s","eventType":"contract.signed","eventVersion":1,"occurredAt":"2026-11-10T03:00:00Z",
                 "producer":"contract-service","aggregateId":"%s",
                 "payload":{"contractId":"%s","listingId":"%s","reservedQuantity":%s,"commodity":"COFFEE",
                            "buyerId":"%s","sellerId":"%s","totalAmount":"225000000.00",
                            "buyerDepositAmount":"22500000.00","sellerDepositAmount":"22500000.00",
                            "signedContentHash":"%s","signedAt":"2026-11-10T03:00:00Z"}}
                """.formatted(EVENT_ID, CONTRACT_ID, CONTRACT_ID, LISTING_ID, quantity, BUYER_ID, SELLER_ID,
                "a".repeat(64));
    }

    private static Message message(String body) {
        return new Message(body.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }
}
