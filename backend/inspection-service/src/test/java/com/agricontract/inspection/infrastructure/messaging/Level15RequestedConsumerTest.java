package com.agricontract.inspection.infrastructure.messaging;

import com.agricontract.eventing.EventEnvelopeCodec;
import com.agricontract.inspection.application.usecase.ManageLevel15Assignments;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class Level15RequestedConsumerTest {
    private final ManageLevel15Assignments lifecycle = mock(ManageLevel15Assignments.class);
    private final Level15RequestedConsumer consumer = new Level15RequestedConsumer(new EventEnvelopeCodec(), lifecycle);
    static final String VALID = """
            {"eventId":"1349fbf6-44b6-450c-b01d-4b5abaf7c326","eventType":"inspection.level1_5_requested",
             "eventVersion":1,"occurredAt":"2026-10-24T00:00:00Z","producer":"contract-service","aggregateId":"contract",
             "payload":{"contractId":"1349fbf6-44b6-450c-b01d-4b5abaf7c327","milestoneId":"1349fbf6-44b6-450c-b01d-4b5abaf7c328",
              "reason":"DISPUTE_ROUTED","reportDueBusinessDays":2,"initiatedByActorId":null,"requestedAt":"2026-10-24T00:00:00Z"}}
            """;
    @Test void validCanonicalEventUsesSharedCodecAndApplicationBoundary() {
        consumer.accept(message(VALID));
        verify(lifecycle).requested(any(), any());
    }
    @Test void malformedAndInspectorOverridePayloadsAreRejectedWithoutMutation() {
        for (String invalid : new String[]{"{", VALID.replace("\"eventVersion\":1", "\"eventVersion\":1.5"),
                VALID.replace("\"reportDueBusinessDays\":2", "\"reportDueBusinessDays\":0"),
                VALID.replace("\"initiatedByActorId\":null,", ""),
                VALID.replace("\"requestedAt\":", "\"inspectorId\":\"chosen\",\"requestedAt\":"),
                VALID.replace("contract-service", "ADMIN"),
                VALID.replace("contract-service", "OPERATOR"),
                VALID.replace("contract-service", "ARBITRATOR")}) {
            assertThatThrownBy(() -> consumer.accept(message(invalid))).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        }
        verifyNoInteractions(lifecycle);
    }
    @Test void databaseOrDependencyFailuresRemainRetryable() {
        doThrow(new IllegalStateException("unavailable")).when(lifecycle).requested(any(), any());
        assertThatThrownBy(() -> consumer.accept(message(VALID))).isInstanceOf(IllegalStateException.class);
    }
    private static Message message(String json) { return new Message(json.getBytes(StandardCharsets.UTF_8)); }
}
