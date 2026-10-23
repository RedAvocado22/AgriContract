package com.agricontract.eventing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class EventEnvelopeTest {

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final Instant OCCURRED_AT = Instant.parse("2026-10-23T03:00:00Z");

    /** All-valid defaults; each test overrides only the field under test. */
    private static class Fields {
        UUID eventId = EVENT_ID;
        String eventType = "contract.signed";
        int eventVersion = 1;
        Instant occurredAt = OCCURRED_AT;
        String producer = "contract-service";
        String aggregateId = "contract-1";
        String correlationId = "corr-1";
        String causationId = null;
        EventSubject subject = new EventSubject(SubjectType.CONTRACT, "contract-1");
        Map<String, Object> payload = Map.of("contractId", "contract-1");

        EventEnvelope build() {
            return new EventEnvelope(eventId, eventType, eventVersion, occurredAt, producer, aggregateId,
                    correlationId, causationId, subject, payload);
        }
    }

    @Test
    void create_allFieldsValid_keepsEveryValue() {
        Fields f = new Fields();
        f.causationId = "cause-1";

        EventEnvelope envelope = f.build();

        assertThat(envelope.eventId()).isEqualTo(EVENT_ID);
        assertThat(envelope.eventType()).isEqualTo("contract.signed");
        assertThat(envelope.eventVersion()).isEqualTo(1);
        assertThat(envelope.occurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(envelope.producer()).isEqualTo("contract-service");
        assertThat(envelope.aggregateId()).isEqualTo("contract-1");
        assertThat(envelope.correlationId()).isEqualTo("corr-1");
        assertThat(envelope.causationId()).isEqualTo("cause-1");
        assertThat(envelope.subject()).isEqualTo(new EventSubject(SubjectType.CONTRACT, "contract-1"));
        assertThat(envelope.payload()).containsEntry("contractId", "contract-1");
    }

    @Test
    void create_optionalFieldsNull_isValid() {
        Fields f = new Fields();
        f.correlationId = null;
        f.causationId = null;
        f.subject = null;

        assertThatCode(f::build).doesNotThrowAnyException();
    }

    @Test
    void create_emptyPayload_isValid() {
        Fields f = new Fields();
        f.payload = Map.of();

        assertThatCode(f::build).doesNotThrowAnyException();
    }

    @Test
    void create_blankButNonEmptyText_isValidPerMinLength() {
        Fields f = new Fields();
        f.producer = " ";
        f.correlationId = " ";

        assertThatCode(f::build).doesNotThrowAnyException();
    }

    @Test
    void create_nullEventId_throws() {
        Fields f = new Fields();
        f.eventId = null;

        assertThatThrownBy(f::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void create_missingEventType_throws(String eventType) {
        Fields f = new Fields();
        f.eventType = eventType;

        assertThatThrownBy(f::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventType");
    }

    @ParameterizedTest
    @ValueSource(strings = {"contract", "contract.", ".signed", "Contract.signed", "contract.Signed",
            "1contract.signed", "contract-signed", "contract.signed ", "contract..signed"})
    void create_eventTypeNotMatchingPattern_throws(String eventType) {
        Fields f = new Fields();
        f.eventType = eventType;

        assertThatThrownBy(f::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventType");
    }

    @ParameterizedTest
    @ValueSource(strings = {"contract.signed", "escrow.deposit_locked", "milestone.dispute_resolved", "bank.v2.lock_1"})
    void create_eventTypeMatchingPattern_isValid(String eventType) {
        Fields f = new Fields();
        f.eventType = eventType;

        assertThatCode(f::build).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void create_eventVersionBelowOne_throws(int eventVersion) {
        Fields f = new Fields();
        f.eventVersion = eventVersion;

        assertThatThrownBy(f::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventVersion");
    }

    @Test
    void create_nullOccurredAt_throws() {
        Fields f = new Fields();
        f.occurredAt = null;

        assertThatThrownBy(f::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("occurredAt");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void create_missingProducer_throws(String producer) {
        Fields f = new Fields();
        f.producer = producer;

        assertThatThrownBy(f::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("producer");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void create_missingAggregateId_throws(String aggregateId) {
        Fields f = new Fields();
        f.aggregateId = aggregateId;

        assertThatThrownBy(f::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aggregateId");
    }

    @Test
    void create_emptyCorrelationId_throws() {
        Fields f = new Fields();
        f.correlationId = "";

        assertThatThrownBy(f::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correlationId");
    }

    @Test
    void create_emptyCausationId_throws() {
        Fields f = new Fields();
        f.causationId = "";

        assertThatThrownBy(f::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("causationId");
    }

    @Test
    void create_nullPayload_throws() {
        Fields f = new Fields();
        f.payload = null;

        assertThatThrownBy(f::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payload");
    }
}
