package com.agricontract.contract.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.agricontract.contract.application.exception.InvalidEventPayloadException;
import com.agricontract.contract.application.usecase.CompleteActivationFailureUseCase;
import com.agricontract.eventing.EventEnvelope;
import com.agricontract.eventing.EventEnvelopeCodec;
import com.agricontract.eventing.inbox.ProcessedEventStore;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

/** escrow.activation_refund_completed is parsed against the frozen schema before anything happens. */
class ActivationRefundCompletedConsumerTest {
    private static final String CONTRACT = UUID.randomUUID().toString();

    private final ActivationRefundCompletedConsumer consumer = new ActivationRefundCompletedConsumer(
            new EventEnvelopeCodec(), mock(ProcessedEventStore.class), mock(CompleteActivationFailureUseCase.class),
            mock(TransactionTemplate.class));

    @Test
    void readsTheContractOfAValidEvent_withOrWithoutReturnedLegs() {
        assertThat(consumer.contractId(envelope(payload(List.of())))).isEqualTo(CONTRACT);
        assertThat(consumer.contractId(envelope(payload(List.of(leg()))))).isEqualTo(CONTRACT);
    }

    @Test
    void refusesWhatTheSchemaDoesNotAllow() {
        Map<String, Object> extra = new HashMap<>(payload(List.of()));
        extra.put("note", "x");
        Map<String, Object> badLeg = payload(List.of(Map.of("fundType", "BUYER_DEPOSIT")));
        Map<String, Object> threeLegs = payload(List.of(leg(), leg(), leg()));
        for (Map<String, Object> payload : List.of(extra, badLeg, threeLegs)) {
            assertThatThrownBy(() -> consumer.contractId(envelope(payload)))
                    .isInstanceOf(InvalidEventPayloadException.class);
        }
        EventEnvelope otherProducer = new EventEnvelope(UUID.randomUUID(), "escrow.activation_refund_completed", 1,
                Instant.now(), "bank-service", CONTRACT, null, null, null, payload(List.of()));
        assertThatThrownBy(() -> consumer.contractId(otherProducer)).isInstanceOf(InvalidEventPayloadException.class);
    }

    private static EventEnvelope envelope(Map<String, Object> payload) {
        return new EventEnvelope(UUID.randomUUID(), "escrow.activation_refund_completed", 1, Instant.now(),
                "escrow-service", CONTRACT, CONTRACT, UUID.randomUUID().toString(), null, payload);
    }

    private static Map<String, Object> payload(List<Map<String, Object>> legs) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contractId", CONTRACT);
        payload.put("returnedLegs", legs);
        payload.put("completedAt", "2026-11-09T03:00:00Z");
        return payload;
    }

    private static Map<String, Object> leg() {
        return Map.of("fundType", "BUYER_DEPOSIT", "userId", UUID.randomUUID().toString(), "amount", "11877375.00",
                "ledgerEntryId", UUID.randomUUID().toString());
    }
}
