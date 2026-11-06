package com.agricontract.inspection.infrastructure.messaging;

import com.agricontract.eventing.EventEnvelopeCodec;
import com.agricontract.inspection.application.usecase.ManageLevel15Assignments;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import com.agricontract.inspection.application.exception.InvalidEventPayloadException;
import org.springframework.amqp.core.Message;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class Level15RequestedConsumerTest {
    private final ManageLevel15Assignments lifecycle = mock(ManageLevel15Assignments.class);
    private final com.agricontract.eventing.inbox.ProcessedEventStore processed = mock(com.agricontract.eventing.inbox.ProcessedEventStore.class);
    private final org.springframework.transaction.support.TransactionTemplate transaction = new org.springframework.transaction.support.TransactionTemplate(mock(org.springframework.transaction.PlatformTransactionManager.class));
    @org.junit.jupiter.api.BeforeEach void setup() { when(processed.markProcessed(anyString(), any())).thenReturn(true); }
    private final Level15RequestedConsumer consumer = new Level15RequestedConsumer(new EventEnvelopeCodec(), lifecycle, processed, transaction);
    static final String VALID = """
            {"eventId":"1349fbf6-44b6-450c-b01d-4b5abaf7c326","eventType":"inspection.level1_5_requested",
             "eventVersion":1,"occurredAt":"2026-10-24T00:00:00Z","producer":"contract-service","aggregateId":"contract",
             "payload":{"contractId":"1349fbf6-44b6-450c-b01d-4b5abaf7c327","milestoneId":"1349fbf6-44b6-450c-b01d-4b5abaf7c328",
              "reason":"DISPUTE_ROUTED","reportDueBusinessDays":2,"initiatedByActorId":null,"requestedAt":"2026-10-24T00:00:00Z"}}
            """;
    @Test void validCanonicalEventUsesSharedCodecAndApplicationBoundary() {
        consumer.accept(message(VALID));
        var order = inOrder(processed, lifecycle);
        order.verify(processed).markProcessed(eq("inspection.level1_5_requested"), any());
        order.verify(lifecycle).requested(new com.agricontract.inspection.application.dto.Level15AssignmentRequestedCommand(
                "1349fbf6-44b6-450c-b01d-4b5abaf7c327", "1349fbf6-44b6-450c-b01d-4b5abaf7c328",
                2, null, "1349fbf6-44b6-450c-b01d-4b5abaf7c326"));
    }
    @Test void malformedAndInspectorOverridePayloadsAreRejectedWithoutMutation() {
        for (String invalid : new String[]{"{", VALID.replace("\"eventVersion\":1", "\"eventVersion\":1.5"),
                VALID.replace("\"reportDueBusinessDays\":2", "\"reportDueBusinessDays\":0"),
                VALID.replace("\"initiatedByActorId\":null,", ""),
                VALID.replace("\"requestedAt\":", "\"inspectorId\":\"chosen\",\"requestedAt\":"),
                VALID.replace("contract-service", "ADMIN"),
                VALID.replace("contract-service", "OPERATOR"),
                VALID.replace("contract-service", "ARBITRATOR")}) {
            assertThatThrownBy(() -> consumer.accept(message(invalid))).isInstanceOf(InvalidEventPayloadException.class);
        }
        verifyNoInteractions(lifecycle, processed);
    }
    @Test void databaseOrDependencyFailuresRemainRetryable() {
        doThrow(new IllegalStateException("unavailable")).when(lifecycle).requested(any());
        assertThatThrownBy(() -> consumer.accept(message(VALID))).isInstanceOf(IllegalStateException.class);
    }
    @Test void duplicateClaimDoesNotInvokeUseCase() {
        when(processed.markProcessed(anyString(), any())).thenReturn(false);
        consumer.accept(message(VALID));
        verifyNoInteractions(lifecycle);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("invalidFields")
    void rejectsInvalidContractFields(String invalid) {
        assertThatThrownBy(() -> consumer.accept(message(invalid))).isInstanceOf(InvalidEventPayloadException.class);
        verifyNoInteractions(lifecycle, processed);
    }

    static java.util.stream.Stream<String> invalidFields() {
        return java.util.stream.Stream.of(
                VALID.replace("inspection.level1_5_requested", "other.event"),
                VALID.replace("\"eventVersion\":1", "\"eventVersion\":2"),
                VALID.replace("DISPUTE_ROUTED", "UNKNOWN"),
                VALID.replace("\"reportDueBusinessDays\":2", "\"reportDueBusinessDays\":31"),
                VALID.replace("\"reportDueBusinessDays\":2", "\"reportDueBusinessDays\":\"2\""),
                VALID.replace("\"reportDueBusinessDays\":2", "\"reportDueBusinessDays\":2.5"),
                VALID.replace("1349fbf6-44b6-450c-b01d-4b5abaf7c327", "bad-contract"),
                VALID.replace("1349fbf6-44b6-450c-b01d-4b5abaf7c328", "bad-milestone"),
                VALID.replace("\"initiatedByActorId\":null", "\"initiatedByActorId\":\"bad-actor\""),
                VALID.replace("\"initiatedByActorId\":null", "\"initiatedByActorId\":42"),
                VALID.replace("\"requestedAt\":\"2026-10-24T00:00:00Z\"", "\"requestedAt\":\"bad-date\""),
                VALID.replace("\"reason\":\"DISPUTE_ROUTED\"", "\"reason\":null"));
    }
    private static Message message(String json) { return new Message(json.getBytes(StandardCharsets.UTF_8)); }
}
