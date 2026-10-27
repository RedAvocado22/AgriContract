package com.agricontract.reputation.infrastructure.messaging;
import com.agricontract.eventing.EventEnvelopeCodec;
import com.agricontract.reputation.application.exception.ImmutableFactConflictException;
import com.agricontract.reputation.application.usecase.AppendReputationFact;
import com.agricontract.reputation.application.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
public class ReputationFactConsumerTest {
    static final ObjectMapper JSON = new ObjectMapper();
    AppendReputationFact append;
    ReputationFactConsumer consumer;
    @BeforeEach void setup() { append = mock(AppendReputationFact.class); consumer = new ReputationFactConsumer(new EventEnvelopeCodec(), append); }
    public static ObjectNode event(String type) {
        ObjectNode root = JSON.createObjectNode();
        root.put("eventId", UUID.randomUUID().toString()).put("eventType", type).put("eventVersion", 1)
                .put("occurredAt","2026-10-13T07:00:00.123456789+07:00").put("producer","contract-service")
                .put("aggregateId", UUID.randomUUID().toString()).put("correlationId", UUID.randomUUID().toString());
        ObjectNode payload = root.putObject("payload");
        payload.put("contractId", root.get("aggregateId").asText());
        if (type.equals("contract.settled")) {
            payload.put("buyerId", UUID.randomUUID().toString()).put("sellerId", UUID.randomUUID().toString());
        } else {
            payload.put("milestoneId", UUID.randomUUID().toString()).put("flaggedBy","BUYER")
                    .put("flaggedByUserId", UUID.randomUUID().toString()).put("resolutionFavors","SELLER");
        }
        return root;
    }
    public static Message message(ObjectNode root) {
        return new Message(root.toString().getBytes(StandardCharsets.UTF_8));
    }
    @Test void completionMapsCanonicalIdsAndUtcPrecision() {
        ObjectNode event = event("contract.settled");
        consumer.completed(message(event));
        verify(append).execute(new AppendCompletedContractCommand(UUID.fromString(event.path("payload").path("contractId").asText()),
                UUID.fromString(event.path("payload").path("buyerId").asText()),
                UUID.fromString(event.path("payload").path("sellerId").asText()),
                Instant.parse("2026-10-13T00:00:00.123456789Z"), UUID.fromString(event.path("eventId").asText())));
    }
    @Test void disputeMapsDirectlyWithoutParticipantLookup() {
        ObjectNode event = event("milestone.dispute_resolved");
        consumer.dispute(message(event));
        verify(append).execute(new AppendDisputeOutcomeCommand(UUID.fromString(event.path("payload").path("milestoneId").asText()),
                UUID.fromString(event.path("payload").path("contractId").asText()),
                UUID.fromString(event.path("payload").path("flaggedByUserId").asText()), "SELLER",
                Instant.parse("2026-10-13T00:00:00.123456789Z"), UUID.fromString(event.path("eventId").asText())));
    }
    static Stream<Consumer<ObjectNode>> invalidEnvelopes() {
        return Stream.of(e -> e.remove("eventId"), e -> e.put("eventId","1-1-1-1-1"),
            e -> e.put("eventVersion",0), e -> e.put("eventVersion",2), e -> e.put("eventVersion",1.1),
            e -> e.put("eventVersion",4294967297L),
            e -> e.put("producer","inspection-service"), e -> e.put("eventType","breach.reported"),
            e -> e.put("eventType","remedy.finalized"), e -> e.put("eventType","contract.terminated"),
            e -> e.put("occurredAt","not-time"), e -> e.remove("aggregateId"), e -> e.put("email","private"),
            e -> e.put("payload","wrong-shape"), e -> e.put("correlationId",""),
            e -> e.putObject("subject").put("type","INVALID").put("id","x"),
            e -> ((ObjectNode)e.get("payload")).put("buyerId",123),
            e -> ((ObjectNode)e.get("payload")).put("email","private"));
    }
    @ParameterizedTest @MethodSource("invalidEnvelopes")
    void malformedOrUnapprovedInputDoesNotWrite(Consumer<ObjectNode> mutate) {
        ObjectNode event=event("contract.settled"); mutate.accept(event);
        assertThatThrownBy(() -> consumer.completed(message(event))).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(append);
    }
    @ParameterizedTest
    @ValueSource(strings = {"contract.settled", "milestone.dispute_resolved"})
    void stringEventVersionIsRejectedWithoutAppending(String type) {
        var event = event(type);
        event.put("eventVersion", "1");
        Consumer<Message> listener = type.equals("contract.settled") ? consumer::completed : consumer::dispute;
        assertThatThrownBy(() -> listener.accept(message(event)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(append);
    }
    @Test void invalidEnvelopeIsTranslatedWithoutExposingValuesOrCause() {
        var event = event("contract.settled");
        event.put("eventVersion", "private-buyer@example.test");
        assertThatThrownBy(() -> consumer.completed(message(event)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessage("Invalid or conflicting reputation fact")
                .hasNoCause();
        verifyNoInteractions(append);
    }
    @Test void badDisputeRoleRejected() {
        var e=event("milestone.dispute_resolved"); ((ObjectNode)e.get("payload")).put("flaggedBy","SELLER");
        assertThatThrownBy(() -> consumer.dispute(message(e))).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(append);
    }
    @Test void conflictingContentIsPermanentButDatabaseOutageIsRetryable() {
        when(append.execute(any(AppendCompletedContractCommand.class))).thenThrow(new ImmutableFactConflictException());
        assertThatThrownBy(() -> consumer.completed(message(event("contract.settled"))))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        when(append.execute(any(AppendCompletedContractCommand.class))).thenThrow(new DataAccessResourceFailureException("offline"));
        assertThatThrownBy(() -> consumer.completed(message(event("contract.settled"))))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }
    @Test void invalidJsonAndNullArePermanent() {
        String valid = event("contract.settled").toString();
        for (String body : new String[]{"null","{","[]", valid + "{}", valid.replaceFirst("\\{", "{\"eventVersion\":2,")}) {
            assertThatThrownBy(() -> consumer.completed(new Message(body.getBytes(StandardCharsets.UTF_8))))
                    .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        }
        verifyNoInteractions(append);
    }
}
