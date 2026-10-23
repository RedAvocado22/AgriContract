package com.agricontract.eventing;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.util.stream.Collectors;

/**
 * The only place envelopes are turned into JSON and back. It owns its mapper so the bytes do not depend
 * on any service's Spring Jackson settings, and parsing is strict to match additionalProperties: false.
 */
public class EventEnvelopeCodec {

    private final ObjectMapper mapper;

    public EventEnvelopeCodec() {
        this.mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
                .build();
    }

    public String toJson(EventEnvelope envelope) {
        try {
            return mapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new InvalidEnvelopeException("Event envelope could not be serialized" + location(e));
        }
    }

    public EventEnvelope parse(byte[] json) {
        try {
            JsonNode tree = mapper.readTree(json);
            if (tree == null || !tree.isObject()) {
                throw new InvalidEnvelopeException("Event envelope must be a JSON object");
            }
            // The schema allows subject to be absent, never null; the record alone cannot tell the two apart.
            if (tree.has("subject") && tree.get("subject").isNull()) {
                throw new InvalidEnvelopeException("subject must be absent rather than null");
            }
            return mapper.treeToValue(tree, EventEnvelope.class);
        } catch (IOException e) {
            throw new InvalidEnvelopeException("Event envelope is malformed or violates the schema" + location(e));
        }
    }

    /**
     * Field path plus our own validation message, both free of field values. Jackson's message and cause are
     * dropped on purpose: they quote the offending value, which may be PII, and listeners log the exception.
     */
    private static String location(IOException e) {
        StringBuilder detail = new StringBuilder();
        if (e instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
            String path = mapping.getPath().stream()
                    .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                    .collect(Collectors.joining("."));
            detail.append(" at ").append(path);
        }
        if (e.getCause() instanceof IllegalArgumentException invalid) {
            detail.append(": ").append(invalid.getMessage());
        }
        return detail.toString();
    }
}
