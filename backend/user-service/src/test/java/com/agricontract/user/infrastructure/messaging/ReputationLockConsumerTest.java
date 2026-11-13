package com.agricontract.user.infrastructure.messaging;

import com.agricontract.eventing.EventEnvelope;
import com.agricontract.eventing.EventEnvelopeCodec;
import com.agricontract.user.application.dto.ApplyLockChangeCommand;
import com.agricontract.user.application.exception.InvalidEventPayloadException;
import com.agricontract.user.domain.model.vo.LockAction;
import com.agricontract.user.infrastructure.contract.FrozenSchemas;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReputationLockConsumerTest {

    private static final String USER_ID = "7d4c2f1e-3b5a-4c6d-8e9f-0a1b2c3d4e5f";
    private static final Instant UNTIL = Instant.parse("2026-12-31T16:59:59.999Z");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final EventEnvelopeCodec CODEC = new EventEnvelopeCodec();

    // toCommand never touches the collaborators.
    private final ReputationLockConsumer consumer = new ReputationLockConsumer(CODEC, null, null, null);

    @Test
    void fixtures_matchFrozenSchemas() throws Exception {
        FrozenSchemas.assertEvent("reputationLocked",
                JSON.readTree(CODEC.toJson(ReputationLockEvents.locked(USER_ID, UNTIL, 1))));
        FrozenSchemas.assertEvent("reputationUnlocked",
                JSON.readTree(CODEC.toJson(ReputationLockEvents.unlocked(USER_ID, UNTIL, 2))));
        FrozenSchemas.assertEvent("reputationUnlocked",
                JSON.readTree(CODEC.toJson(ReputationLockEvents.unlocked(USER_ID, null, 3))));
    }

    @Test
    void locked_mapsToLockCommandCarryingTheChain() {
        EventEnvelope event = ReputationLockEvents.locked(USER_ID.toUpperCase(), UNTIL, 4);

        ApplyLockChangeCommand command = consumer.toCommand(event);

        assertThat(command.action()).isEqualTo(LockAction.LOCKED);
        assertThat(command.userId()).isEqualTo(USER_ID);
        assertThat(command.until()).isEqualTo(UNTIL);
        assertThat(command.lockRevision()).isEqualTo(4);
        assertThat(command.reasonCode()).isEqualTo("SIDE_SELLING");
        assertThat(command.sourceEventId()).isEqualTo(event.eventId().toString());
        assertThat(command.correlationId()).isEqualTo(event.correlationId());
    }

    @Test
    void unlocked_withNullEffectiveLock_mapsToFullUnlock() {
        ApplyLockChangeCommand command = consumer.toCommand(ReputationLockEvents.unlocked(USER_ID, null, 2));

        assertThat(command.action()).isEqualTo(LockAction.UNLOCKED);
        assertThat(command.until()).isNull();
    }

    @Test
    void offsetDateTime_isNormalisedToUtcMicros() {
        Map<String, Object> payload = payloadOf(ReputationLockEvents.locked(USER_ID, UNTIL, 1));
        payload.put("lockedUntil", "2027-01-01T06:59:59.9999999+07:00");

        ApplyLockChangeCommand command = consumer.toCommand(
                ReputationLockEvents.withPayload(ReputationLockEvents.locked(USER_ID, UNTIL, 1), payload));

        assertThat(command.until()).isEqualTo(Instant.parse("2026-12-31T23:59:59.999999Z"));
    }

    @Test
    void missingCorrelationId_startsTheChainAtThisEvent() {
        EventEnvelope source = ReputationLockEvents.locked(USER_ID, UNTIL, 1);
        EventEnvelope event = new EventEnvelope(source.eventId(), source.eventType(), 1, source.occurredAt(),
                source.producer(), source.aggregateId(), null, null, null, source.payload());

        assertThat(consumer.toCommand(event).correlationId()).isEqualTo(event.eventId().toString());
    }

    @Test
    void lockedWithoutLockedUntil_isRejected() {
        Map<String, Object> payload = payloadOf(ReputationLockEvents.locked(USER_ID, UNTIL, 1));
        payload.put("lockedUntil", null);

        assertInvalid(payload);
    }

    @Test
    void unlockedWithoutEffectiveLockedUntilKey_isRejected() {
        EventEnvelope source = ReputationLockEvents.unlocked(USER_ID, null, 2);
        Map<String, Object> payload = new LinkedHashMap<>(source.payload());
        payload.remove("effectiveLockedUntil");

        assertThatThrownBy(() -> consumer.toCommand(ReputationLockEvents.withPayload(source, payload)))
                .isInstanceOf(InvalidEventPayloadException.class);
    }

    @Test
    void extraField_isRejected() {
        Map<String, Object> payload = payloadOf(ReputationLockEvents.locked(USER_ID, UNTIL, 1));
        payload.put("email", "seller@example.com");

        assertInvalid(payload);
    }

    @Test
    void revisionBelowOne_orNotAnInteger_isRejected() {
        Map<String, Object> zero = payloadOf(ReputationLockEvents.locked(USER_ID, UNTIL, 1));
        zero.put("lockRevision", 0);
        assertInvalid(zero);

        Map<String, Object> decimal = payloadOf(ReputationLockEvents.locked(USER_ID, UNTIL, 1));
        decimal.put("lockRevision", 2.0);
        assertInvalid(decimal);

        Map<String, Object> text = payloadOf(ReputationLockEvents.locked(USER_ID, UNTIL, 1));
        text.put("lockRevision", "2");
        assertInvalid(text);
    }

    @Test
    void nonUuidUser_isRejected() {
        Map<String, Object> payload = payloadOf(ReputationLockEvents.locked(USER_ID, UNTIL, 1));
        payload.put("userId", "seller1");

        assertInvalid(payload);
    }

    @Test
    void otherProducerOrType_isRejected() {
        EventEnvelope source = ReputationLockEvents.locked(USER_ID, UNTIL, 1);
        EventEnvelope wrongProducer = new EventEnvelope(source.eventId(), source.eventType(), 1, source.occurredAt(),
                "user-service", source.aggregateId(), null, null, null, source.payload());
        EventEnvelope wrongType = new EventEnvelope(source.eventId(), "reputation.score_changed", 1,
                source.occurredAt(), source.producer(), source.aggregateId(), null, null, null, source.payload());

        assertThatThrownBy(() -> consumer.toCommand(wrongProducer)).isInstanceOf(InvalidEventPayloadException.class);
        assertThatThrownBy(() -> consumer.toCommand(wrongType)).isInstanceOf(InvalidEventPayloadException.class);
    }

    private static Map<String, Object> payloadOf(EventEnvelope event) {
        return new LinkedHashMap<>(event.payload());
    }

    private void assertInvalid(Map<String, Object> payload) {
        EventEnvelope event = ReputationLockEvents.withPayload(ReputationLockEvents.locked(USER_ID, UNTIL, 1), payload);
        assertThatThrownBy(() -> consumer.toCommand(event)).isInstanceOf(InvalidEventPayloadException.class);
    }
}
