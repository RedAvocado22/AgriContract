package com.agricontract.bank.infrastructure.messaging;

import com.agricontract.bank.application.dto.LockFundsCommand;
import com.agricontract.bank.domain.model.EntryType;
import com.agricontract.bank.domain.model.FundType;
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

class LockRequestConsumerTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final LockRequestConsumer consumer = new LockRequestConsumer(new EventEnvelopeCodec(), command -> { });

    @Test
    void readsAValidRequest() throws Exception {
        Map<String, Object> event = validRequest();
        LockFundsCommand command = consumer.toCommand(JSON.writeValueAsBytes(event));

        @SuppressWarnings("unchecked") Map<String, Object> payload = (Map<String, Object>) event.get("payload");
        assertThat(command.requestEventId()).isEqualTo(event.get("eventId"));
        assertThat(command.sourceEventId()).isEqualTo(event.get("eventId"));
        assertThat(command.correlationId()).isEqualTo(event.get("correlationId"));
        assertThat(command.contractId()).isEqualTo(payload.get("contractId"));
        assertThat(command.milestoneId()).isNull();
        assertThat(command.entryType()).isEqualTo(EntryType.LOCK_BUYER_DEPOSIT);
        assertThat(command.fundType()).isEqualTo(FundType.BUYER_DEPOSIT);
        assertThat(command.amount()).isEqualByComparingTo(new BigDecimal("40000000.00"));
    }

    /** Every way a message can fail the frozen schema or the bank command rules ends as a DLQ-bound rejection. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("brokenRequests")
    void rejectsWhatTheContractDoesNotAllow(String why, Consumer<Map<String, Object>> breakIt) throws Exception {
        Map<String, Object> event = validRequest();
        breakIt.accept(event);
        byte[] body = JSON.writeValueAsBytes(event);

        assertThatThrownBy(() -> consumer.toCommand(body)).isInstanceOf(InvalidEventPayloadException.class);
    }

    @SuppressWarnings("unchecked")
    static Stream<Arguments> brokenRequests() {
        return Stream.of(
                Arguments.of("sourceEventId differs from eventId",
                        (Consumer<Map<String, Object>>) e -> payload(e).put("sourceEventId", id())),
                Arguments.of("missing correlationId", (Consumer<Map<String, Object>>) e -> e.remove("correlationId")),
                Arguments.of("not a lock", (Consumer<Map<String, Object>>) e -> payload(e).put("entryType", "RELEASE_TO_SELLER")),
                Arguments.of("amount with one decimal", (Consumer<Map<String, Object>>) e -> payload(e).put("amount", "100.5")),
                Arguments.of("amount as a number", (Consumer<Map<String, Object>>) e -> payload(e).put("amount", 100)),
                Arguments.of("zero amount", (Consumer<Map<String, Object>>) e -> payload(e).put("amount", "0.00")),
                Arguments.of("required nullable key absent", (Consumer<Map<String, Object>>) e -> payload(e).remove("remedyLegId")),
                Arguments.of("unknown payload field", (Consumer<Map<String, Object>>) e -> payload(e).put("note", "x")),
                Arguments.of("wrong producer", (Consumer<Map<String, Object>>) e -> e.put("producer", "contract-service")),
                Arguments.of("not JSON at all", (Consumer<Map<String, Object>>) e -> e.put("eventVersion", "one")));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payload(Map<String, Object> event) {
        return (Map<String, Object>) event.get("payload");
    }

    static Map<String, Object> validRequest() {
        String eventId = id();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contractId", id());
        payload.put("milestoneId", null);
        payload.put("userId", id());
        payload.put("entryType", "LOCK_BUYER_DEPOSIT");
        payload.put("fundType", "BUYER_DEPOSIT");
        payload.put("remedyDecisionId", null);
        payload.put("remedyLegId", null);
        payload.put("amount", "40000000.00");
        payload.put("sourceEventId", eventId);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", eventId);
        event.put("eventType", "bank.lock_requested");
        event.put("eventVersion", 1);
        event.put("occurredAt", "2026-10-27T03:00:00Z");
        event.put("producer", "escrow-service");
        event.put("aggregateId", payload.get("contractId"));
        event.put("correlationId", id());
        event.put("payload", payload);
        return event;
    }

    private static String id() {
        return UUID.randomUUID().toString();
    }
}
