package com.agricontract.eventing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class EventEnvelopeCodecTest {

    private static final String EVENT_ID = "3f1c2d4e-5a6b-4c7d-8e9f-0a1b2c3d4e5f";

    private final EventEnvelopeCodec codec = new EventEnvelopeCodec();
    private final ObjectMapper plain = new ObjectMapper();

    private static EventEnvelope envelope(EventSubject subject, String correlationId) {
        return new EventEnvelope(UUID.fromString(EVENT_ID), "contract.signed", 1,
                Instant.parse("2026-10-23T03:00:00Z"), "contract-service", "contract-1",
                correlationId, null, subject, Map.of("contractId", "contract-1"));
    }

    private static String validJson(String extra) {
        return """
                {"eventId":"%s","eventType":"contract.signed","eventVersion":1,
                 "occurredAt":"2026-10-23T03:00:00Z","producer":"contract-service",
                 "aggregateId":"contract-1","payload":{"contractId":"contract-1"}%s}
                """.formatted(EVENT_ID, extra);
    }

    private static byte[] bytes(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void toJson_occurredAt_isIsoStringWithZ() throws Exception {
        JsonNode json = plain.readTree(codec.toJson(envelope(null, "corr-1")));

        assertThat(json.get("occurredAt").isTextual()).isTrue();
        assertThat(json.get("occurredAt").asText()).isEqualTo("2026-10-23T03:00:00Z");
    }

    @Test
    void level15RequestPreservesRequiredNullableActorWithoutInventingMissingFields() throws Exception {
        var payload = new java.util.LinkedHashMap<String, Object>();
        payload.put("contractId", EVENT_ID);
        payload.put("milestoneId", "1349fbf6-44b6-450c-b01d-4b5abaf7c328");
        payload.put("reason", "DISPUTE_ROUTED");
        payload.put("reportDueBusinessDays", 2);
        payload.put("initiatedByActorId", null);
        payload.put("requestedAt", "2026-10-23T03:00:00Z");
        var event = new EventEnvelope(UUID.fromString(EVENT_ID), "inspection.level1_5_requested", 1,
                Instant.parse("2026-10-23T03:00:00Z"), "contract-service", EVENT_ID, null, null, null, payload);
        JsonNode wire = plain.readTree(codec.toJson(event));
        assertThat(wire.path("payload").has("initiatedByActorId")).isTrue();
        assertThat(wire.path("payload").get("initiatedByActorId").isNull()).isTrue();
        assertThat(wire.has("subject")).isFalse();
        assertThat(codec.parse(bytes(codec.toJson(event))).payload()).containsKey("initiatedByActorId");
        payload.remove("initiatedByActorId");
        assertThat(plain.readTree(codec.toJson(event)).path("payload").has("initiatedByActorId")).isFalse();
    }

    @Test
    void toJson_nullOptionalFields_areAbsentNotNull() throws Exception {
        JsonNode json = plain.readTree(codec.toJson(envelope(null, null)));

        assertThat(json.has("subject")).isFalse();
        assertThat(json.has("correlationId")).isFalse();
        assertThat(json.has("causationId")).isFalse();
    }

    @Test
    void toJson_nullPayloadValues_stayOnTheWire() throws Exception {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("remedyLegId", null);
        EventEnvelope envelope = new EventEnvelope(UUID.fromString(EVENT_ID), "bank.lock_completed", 1,
                Instant.parse("2026-10-23T03:00:00Z"), "bank-service", "contract-1", null, null, null, payload);

        JsonNode json = plain.readTree(codec.toJson(envelope));

        assertThat(json.get("payload").has("remedyLegId")).isTrue();
        assertThat(json.get("payload").get("remedyLegId").isNull()).isTrue();
        assertThat(json.has("correlationId")).isFalse();
    }

    @Test
    void toJson_emitsOnlySchemaFieldNames() throws Exception {
        JsonNode json = plain.readTree(codec.toJson(envelope(new EventSubject(SubjectType.CONTRACT, "contract-1"), "corr-1")));

        assertThat(json.fieldNames()).toIterable().containsExactlyInAnyOrder(
                "eventId", "eventType", "eventVersion", "occurredAt", "producer", "aggregateId",
                "correlationId", "subject", "payload");
        assertThat(json.get("subject").get("type").asText()).isEqualTo("CONTRACT");
    }

    @Test
    void toJsonThenParse_roundTripsToEqualEnvelope() {
        EventEnvelope original = envelope(new EventSubject(SubjectType.USER_PAIR, "pair-1"), "corr-1");

        EventEnvelope parsed = codec.parse(bytes(codec.toJson(original)));

        assertThat(parsed).isEqualTo(original);
    }

    @Test
    void toJson_sameEnvelope_producesIdenticalString() {
        EventEnvelope envelope = envelope(null, "corr-1");

        assertThat(codec.toJson(envelope)).isEqualTo(codec.toJson(envelope));
    }

    @Test
    void parse_minimalValidJson_succeeds() {
        EventEnvelope parsed = codec.parse(bytes(validJson("")));

        assertThat(parsed.eventId()).isEqualTo(UUID.fromString(EVENT_ID));
        assertThat(parsed.subject()).isNull();
        assertThat(parsed.correlationId()).isNull();
    }

    @Test
    void parse_explicitNullCorrelationAndCausation_isValid() {
        assertThatCode(() -> codec.parse(bytes(validJson(",\"correlationId\":null,\"causationId\":null"))))
                .doesNotThrowAnyException();
    }

    @Test
    void parse_explicitNullSubject_throws() {
        assertThatThrownBy(() -> codec.parse(bytes(validJson(",\"subject\":null"))))
                .isInstanceOf(InvalidEnvelopeException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            ",\"extra\":1",                                     // unknown envelope field
            ",\"subject\":{\"type\":\"CONTRACT\",\"id\":\"c\",\"x\":1}", // unknown subject field
            ",\"subject\":{\"type\":\"BUYER\",\"id\":\"c\"}",    // subject type outside enum
            ",\"eventId\":\"" + EVENT_ID + "\""                 // duplicate key
    })
    void parse_schemaViolation_throws(String extra) {
        assertThatThrownBy(() -> codec.parse(bytes(validJson(extra))))
                .isInstanceOf(InvalidEnvelopeException.class);
    }

    @Test
    void parse_missingEventVersion_throws() {
        String json = validJson("").replace("\"eventVersion\":1,", "");

        assertThatThrownBy(() -> codec.parse(bytes(json)))
                .isInstanceOf(InvalidEnvelopeException.class);
    }

    @Test
    void parse_invalidFieldValue_fromRecordValidation_throwsInvalidEnvelope() {
        String json = validJson("").replace("contract.signed", "Contract.Signed");

        assertThatThrownBy(() -> codec.parse(bytes(json)))
                .isInstanceOf(InvalidEnvelopeException.class);
    }

    @Test
    void parse_occurredAtWithoutOffset_throws() {
        String json = validJson("").replace("2026-10-23T03:00:00Z", "2026-10-23T03:00:00");

        assertThatThrownBy(() -> codec.parse(bytes(json)))
                .isInstanceOf(InvalidEnvelopeException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not json", "[]", "\"text\"", "{\"eventId\":"})
    void parse_notAnEnvelopeObject_throws(String json) {
        assertThatThrownBy(() -> codec.parse(bytes(json)))
                .isInstanceOf(InvalidEnvelopeException.class);
    }

    @Test
    void parse_payloadNotAnObject_throws() {
        String json = validJson("").replace("{\"contractId\":\"contract-1\"}", "\"contract-1\"");

        assertThatThrownBy(() -> codec.parse(bytes(json)))
                .isInstanceOf(InvalidEnvelopeException.class);
    }

    @Test
    void parse_trailingTokens_throws() {
        assertThatThrownBy(() -> codec.parse(bytes(validJson("") + "{}")))
                .isInstanceOf(InvalidEnvelopeException.class);
    }

    @Test
    void parse_failureMessage_doesNotLeakPayload() {
        String json = validJson(",\"extra\":\"buyer@agricontract.test\"");

        assertThatThrownBy(() -> codec.parse(bytes(json)))
                .isInstanceOf(InvalidEnvelopeException.class)
                .message().doesNotContain("buyer@agricontract.test");
    }

    @Test
    void parse_failure_doesNotLeakFieldValueInMessageOrCause() {
        String json = validJson("").replace("\"eventVersion\":1", "\"eventVersion\":\"buyer@agricontract.test\"");

        Throwable thrown = catchThrowable(() -> codec.parse(bytes(json)));

        assertThat(thrown).isInstanceOf(InvalidEnvelopeException.class);
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            assertThat(String.valueOf(t.getMessage())).doesNotContain("buyer@agricontract.test");
        }
    }

    @Test
    void parse_failureMessage_namesTheFieldPath() {
        String json = validJson(",\"subject\":{\"type\":\"BUYER\",\"id\":\"c\"}");

        assertThatThrownBy(() -> codec.parse(bytes(json)))
                .isInstanceOf(InvalidEnvelopeException.class)
                .hasMessageContaining("subject.type");
    }

    @Test
    void parse_recordValidationFailure_keepsOurFieldMessage() {
        String json = validJson("").replace("\"eventVersion\":1", "\"eventVersion\":0");

        assertThatThrownBy(() -> codec.parse(bytes(json)))
                .isInstanceOf(InvalidEnvelopeException.class)
                .hasMessageContaining("eventVersion must be >= 1")
                .hasNoCause();
    }

    // Jackson would coerce each of these into a valid-looking envelope; the schema types reject them.
    @ParameterizedTest
    @ValueSource(strings = {
            "\"eventVersion\":\"1\"",       // string for integer
            "\"eventVersion\":1.9",           // float for integer
            "\"producer\":123",               // number for string
            "\"aggregateId\":true",           // boolean for string
            "\"occurredAt\":1700000000",      // epoch number for date-time
            "\"occurredAt\":1700000000.5",
            "\"occurredAt\":true",
            "\"eventId\":5"                   // number for uuid
    })
    void parse_scalarOfWrongType_throwsInsteadOfCoercing(String field) {
        String name = field.substring(1, field.indexOf('"', 1));
        String json = validJson("").replaceFirst("\"" + name + "\":(\"[^\"]*\"|[0-9]+)", field);

        assertThatThrownBy(() -> codec.parse(bytes(json)))
                .isInstanceOf(InvalidEnvelopeException.class);
    }

    @Test
    void parse_correlationIdNumber_throwsInsteadOfCoercing() {
        assertThatThrownBy(() -> codec.parse(bytes(validJson(",\"correlationId\":5"))))
                .isInstanceOf(InvalidEnvelopeException.class);
    }

    @Test
    void parse_occurredAtNumber_failureNamesTheField() {
        String json = validJson("").replace("\"2026-10-23T03:00:00Z\"", "1700000000");

        assertThatThrownBy(() -> codec.parse(bytes(json)))
                .isInstanceOf(InvalidEnvelopeException.class)
                .hasMessageContaining("occurredAt");
    }
}
