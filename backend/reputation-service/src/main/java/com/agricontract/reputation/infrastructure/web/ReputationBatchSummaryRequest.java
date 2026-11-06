package com.agricontract.reputation.infrastructure.web;

import com.agricontract.reputation.application.dto.BatchSummaryQuery;
import com.agricontract.reputation.domain.model.FactValues;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@JsonDeserialize(using = ReputationBatchSummaryRequest.Deserializer.class)
public record ReputationBatchSummaryRequest(List<UUID> userIds) {
    public ReputationBatchSummaryRequest {
        userIds = new BatchSummaryQuery(userIds).userIds();
    }

    // Local strict decoding: do not change Jackson behavior for existing endpoints or consumers.
    private static ReputationBatchSummaryRequest fromJson(JsonNode body) {
        if (!body.isObject() || body.size() != 1 || !body.has("userIds")) {
            throw new IllegalArgumentException("Expected userIds only");
        }
        JsonNode values = body.get("userIds");
        if (!values.isArray() || values.isEmpty() || values.size() > 100) {
            throw new IllegalArgumentException("Expected 1 to 100 user IDs");
        }
        List<UUID> ids = new ArrayList<>();
        for (JsonNode value : values) {
            if (!value.isTextual()) {
                throw new IllegalArgumentException("Expected UUID string");
            }
            ids.add(FactValues.uuid(value.textValue()));
        }
        return new ReputationBatchSummaryRequest(ids);
    }

    public static final class Deserializer extends JsonDeserializer<ReputationBatchSummaryRequest> {
        @Override
        public ReputationBatchSummaryRequest deserialize(JsonParser parser, DeserializationContext context)
                throws IOException {
            parser.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
            JsonNode body = parser.getCodec().readTree(parser);
            if (parser.nextToken() != null) {
                throw JsonMappingException.from(parser, "Expected one request object");
            }
            try {
                return fromJson(body);
            } catch (IllegalArgumentException invalid) {
                throw JsonMappingException.from(parser, "Invalid batch summary request");
            }
        }
    }
}
