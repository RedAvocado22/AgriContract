package com.agricontract.inspection.infrastructure.hashing;

import com.agricontract.inspection.domain.model.InspectionSettlementResultV1;
import com.agricontract.inspection.domain.model.ReportCommitment;
import com.agricontract.inspection.domain.service.InspectionCanonicalizer;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.erdtman.jcs.JsonCanonicalizer;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Vendor API is confined here. Jackson builds JSON; JCS alone defines canonical bytes. */
@Component
public class JcsCanonicalizer implements InspectionCanonicalizer {
    private final ObjectMapper mapper =
            JsonMapper.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .build();

    @Override
    public byte[] canonicalize(InspectionSettlementResultV1 result) {
        if (result == null) {
            throw new IllegalArgumentException("Result required");
        }
        ObjectNode tree = mapper.valueToTree(result);
        if (result.inconclusiveReasons() == null) {
            tree.remove("inconclusiveReasons");
        }
        return canonicalizeJson(tree.toString());
    }

    @Override
    public byte[] canonicalize(ReportCommitment report) {
        if (report == null) {
            throw new IllegalArgumentException("Report commitment required");
        }
        JsonNode tree = mapper.valueToTree(report);
        return canonicalizeJson(tree.toString());
    }

    public byte[] canonicalizeJson(String json) {
        try {
            // Reject unpaired UTF-16 surrogates, including escaped ones decoded by Jackson.
            validateUnicode(mapper.readTree(json));
            return new JsonCanonicalizer(json).getEncodedUTF8();
        } catch (IOException invalid) {
            throw new IllegalArgumentException("Invalid JCS material");
        }
    }

    private static void validateUnicode(JsonNode node) {
        if (node == null) {
            throw new IllegalArgumentException("JCS material required");
        }
        if (node.isTextual()) {
            validateString(node.textValue());
        }
        if (node.isObject()) {
            node.fieldNames().forEachRemaining(JcsCanonicalizer::validateString);
        }
        if (node.isContainerNode()) {
            node.forEach(JcsCanonicalizer::validateUnicode);
        }
    }

    private static void validateString(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) {
                    throw new IllegalArgumentException("Unpaired Unicode surrogate");
                }
            } else if (Character.isLowSurrogate(c)) {
                throw new IllegalArgumentException("Unpaired Unicode surrogate");
            }
        }
    }
}
