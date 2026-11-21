package com.agricontract.inspection.infrastructure.messaging;

import com.agricontract.eventing.inbox.ProcessedEventStore;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.agricontract.inspection.application.dto.Level15AssignmentRequestedCommand;
import com.agricontract.inspection.testsupport.DirectTransactionRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import java.util.stream.Stream;
import com.agricontract.eventing.EventEnvelopeCodec;
import com.agricontract.inspection.application.usecase.ManageLevel15Assignments;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import com.agricontract.inspection.application.exception.InvalidEventPayloadException;
import org.springframework.amqp.core.Message;
import org.slf4j.LoggerFactory;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class Level15RequestedConsumerTest {
    private final ManageLevel15Assignments lifecycle = mock(ManageLevel15Assignments.class);
    private final ProcessedEventStore processed = mock(ProcessedEventStore.class);
    private final DirectTransactionRunner transaction = new DirectTransactionRunner();
    @BeforeEach void setup() { when(processed.markProcessed(anyString(), any())).thenReturn(true); }
    private final Level15RequestedConsumer consumer = new Level15RequestedConsumer(new EventEnvelopeCodec(), lifecycle, processed, transaction);
    static final String VALID = """
            {"eventId":"1349fbf6-44b6-450c-b01d-4b5abaf7c326","eventType":"inspection.level1_5_requested",
             "correlationId":"1349fbf6-44b6-450c-b01d-4b5abaf7c329","eventVersion":1,"occurredAt":"2026-10-24T00:00:00Z","producer":"contract-service","aggregateId":"contract",
             "payload":{"contractId":"1349fbf6-44b6-450c-b01d-4b5abaf7c327","milestoneId":"1349fbf6-44b6-450c-b01d-4b5abaf7c328",
              "reason":"DISPUTE_ROUTED","reportDueBusinessDays":2,"initiatedByActorId":null,"requestedAt":"2026-10-24T00:00:00Z"}}
            """;
    @Test void validCanonicalEventUsesSharedCodecAndApplicationBoundary() {
        consumer.accept(message(VALID));
        var order = inOrder(processed, lifecycle);
        order.verify(processed).markProcessed(eq("inspection.level1_5_requested"), any());
        order.verify(lifecycle).requested(new Level15AssignmentRequestedCommand(
                "1349fbf6-44b6-450c-b01d-4b5abaf7c327", "1349fbf6-44b6-450c-b01d-4b5abaf7c328",
                2, null, "1349fbf6-44b6-450c-b01d-4b5abaf7c326", "1349fbf6-44b6-450c-b01d-4b5abaf7c329"));
    }
    @ParameterizedTest
    @MethodSource("correlationCases")
    void resolvesCorrelationForNotificationContract(String correlationField, String expected) {
        consumer.accept(message(VALID.replace("\"correlationId\":\"1349fbf6-44b6-450c-b01d-4b5abaf7c329\"", correlationField)));
        verify(lifecycle).requested(new Level15AssignmentRequestedCommand(
                "1349fbf6-44b6-450c-b01d-4b5abaf7c327", "1349fbf6-44b6-450c-b01d-4b5abaf7c328",
                2, null, "1349fbf6-44b6-450c-b01d-4b5abaf7c326", expected));
    }

    static Stream<Arguments> correlationCases() {
        String eventId = "1349fbf6-44b6-450c-b01d-4b5abaf7c326";
        return Stream.of(
                Arguments.of("\"correlationId\":null", eventId),
                Arguments.of("\"correlationId\":\"   \"", eventId),
                Arguments.of("\"correlationId\":\"req-abc\"", eventId),
                Arguments.of("\"correlationId\":\"1-1-1-1-1\"", eventId),
                Arguments.of("\"correlationId\":\"1349FBF6-44B6-450C-B01D-4B5ABAF7C329\"", "1349FBF6-44B6-450C-B01D-4B5ABAF7C329"));
    }
    @Test void invalidCorrelationIsNotLogged() {
        var logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        var logs = new ListAppender<ILoggingEvent>();
        logs.start();
        logger.addAppender(logs);
        try {
            consumer.accept(message(VALID.replace("1349fbf6-44b6-450c-b01d-4b5abaf7c329", "private-untrusted-correlation")));
            assertThat(logs.list).noneMatch(entry -> entry.getFormattedMessage().contains("private-untrusted-correlation"));
            verify(lifecycle).requested(new Level15AssignmentRequestedCommand(
                    "1349fbf6-44b6-450c-b01d-4b5abaf7c327", "1349fbf6-44b6-450c-b01d-4b5abaf7c328",
                    2, null, "1349fbf6-44b6-450c-b01d-4b5abaf7c326", "1349fbf6-44b6-450c-b01d-4b5abaf7c326"));
        } finally {
            logger.detachAppender(logs);
            logs.stop();
        }
    }
    @Test void missingCorrelationUsesSourceEventId() {
        consumer.accept(message(VALID.replace("\"correlationId\":\"1349fbf6-44b6-450c-b01d-4b5abaf7c329\",", "")));
        verify(lifecycle).requested(new Level15AssignmentRequestedCommand(
                "1349fbf6-44b6-450c-b01d-4b5abaf7c327", "1349fbf6-44b6-450c-b01d-4b5abaf7c328",
                2, null, "1349fbf6-44b6-450c-b01d-4b5abaf7c326", "1349fbf6-44b6-450c-b01d-4b5abaf7c326"));
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

    @ParameterizedTest
    @MethodSource("invalidFields")
    void rejectsInvalidContractFields(String invalid) {
        assertThatThrownBy(() -> consumer.accept(message(invalid))).isInstanceOf(InvalidEventPayloadException.class);
        verifyNoInteractions(lifecycle, processed);
    }

    static Stream<String> invalidFields() {
        return Stream.of(
                VALID.replace("1349fbf6-44b6-450c-b01d-4b5abaf7c326", "bad-event-id"),
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
