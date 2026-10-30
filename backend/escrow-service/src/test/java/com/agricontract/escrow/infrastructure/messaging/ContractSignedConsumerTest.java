package com.agricontract.escrow.infrastructure.messaging;

import com.agricontract.escrow.application.dto.ContractSignedCommand;
import com.agricontract.escrow.application.exception.InvalidEventPayloadException;
import com.agricontract.eventing.EventEnvelope;
import com.agricontract.eventing.EventEnvelopeCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContractSignedConsumerTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final EventEnvelopeCodec CODEC = new EventEnvelopeCodec();
    // toCommand only reads the envelope; dedup, the use case and the transaction are covered by the integration test.
    private final ContractSignedConsumer consumer = new ContractSignedConsumer(CODEC, null, null, null);

    @Test
    void readsAValidEvent() throws Exception {
        Map<String, Object> event = validEvent();
        ContractSignedCommand command = consumer.toCommand(envelope(event));

        assertThat(command.eventId()).isEqualTo(event.get("eventId"));
        assertThat(command.correlationId()).isEqualTo(event.get("correlationId"));
        assertThat(command.contractId()).isEqualTo(payload(event).get("contractId"));
        assertThat(command.buyerId()).isEqualTo(payload(event).get("buyerId"));
        assertThat(command.sellerId()).isEqualTo(payload(event).get("sellerId"));
        assertThat(command.buyerDepositAmount()).isEqualByComparingTo(new BigDecimal("40000000.00"));
        assertThat(command.sellerDepositAmount()).isEqualByComparingTo(new BigDecimal("20000000.00"));
    }

    @Test
    void startsTheChainFromTheSignedEventWithoutACorrelationId() throws Exception {
        Map<String, Object> event = validEvent();
        event.remove("correlationId");

        ContractSignedCommand command = consumer.toCommand(envelope(event));

        assertThat(command.correlationId()).isEqualTo(event.get("eventId"));
    }

    @Test
    void acceptsAContractWithoutASellerDeposit() throws Exception {
        Map<String, Object> event = validEvent();
        payload(event).put("sellerDepositAmount", "0.00");

        ContractSignedCommand command = consumer.toCommand(envelope(event));

        assertThat(command.sellerDepositAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    /** Every way a message can fail the frozen contractSigned schema ends as a DLQ-bound rejection. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("brokenEvents")
    void rejectsWhatTheContractDoesNotAllow(String why, Consumer<Map<String, Object>> breakIt) throws Exception {
        Map<String, Object> event = validEvent();
        breakIt.accept(event);
        EventEnvelope envelope = envelope(event);

        assertThatThrownBy(() -> consumer.toCommand(envelope)).isInstanceOf(InvalidEventPayloadException.class);
    }

    static Stream<Arguments> brokenEvents() {
        return Stream.of(
                Arguments.of("wrong event type", broken(e -> e.put("eventType", "contract.activated"))),
                Arguments.of("wrong producer", broken(e -> e.put("producer", "escrow-service"))),
                Arguments.of("unknown version", broken(e -> e.put("eventVersion", 2))),
                Arguments.of("missing required field", broken(e -> payload(e).remove("signedContentHash"))),
                Arguments.of("unknown payload field", broken(e -> payload(e).put("note", "x"))),
                Arguments.of("buyerId not a UUID", broken(e -> payload(e).put("buyerId", "buyer-1"))),
                Arguments.of("sellerId null", broken(e -> payload(e).put("sellerId", null))),
                Arguments.of("deposit with one decimal", broken(e -> payload(e).put("buyerDepositAmount", "100.5"))),
                Arguments.of("deposit as a number", broken(e -> payload(e).put("buyerDepositAmount", 100))),
                Arguments.of("negative deposit", broken(e -> payload(e).put("sellerDepositAmount", "-1.00"))));
    }

    private static Consumer<Map<String, Object>> broken(Consumer<Map<String, Object>> breakIt) {
        return breakIt;
    }

    private static EventEnvelope envelope(Map<String, Object> event) throws Exception {
        return CODEC.parse(JSON.writeValueAsBytes(event));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payload(Map<String, Object> event) {
        return (Map<String, Object>) event.get("payload");
    }

    static Map<String, Object> validEvent() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contractId", id());
        payload.put("listingId", id());
        payload.put("reservedQuantity", 1000);
        payload.put("commodity", "COFFEE");
        payload.put("buyerId", id());
        payload.put("sellerId", id());
        payload.put("totalAmount", "400000000.00");
        payload.put("buyerDepositAmount", "40000000.00");
        payload.put("sellerDepositAmount", "20000000.00");
        payload.put("signedContentHash", "a".repeat(64));
        payload.put("signedAt", "2026-10-31T03:00:00Z");
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", id());
        event.put("eventType", "contract.signed");
        event.put("eventVersion", 1);
        event.put("occurredAt", "2026-10-31T03:00:00Z");
        event.put("producer", "contract-service");
        event.put("aggregateId", payload.get("contractId"));
        event.put("correlationId", id());
        event.put("payload", payload);
        return event;
    }

    private static String id() {
        return UUID.randomUUID().toString();
    }
}
