package com.agricontract.inspection.infrastructure.hashing;

import com.agricontract.inspection.domain.model.CoffeeActualMetrics;
import com.agricontract.inspection.domain.model.CoffeeType;
import com.agricontract.inspection.domain.model.InspectionSettlementResultV1;
import com.agricontract.inspection.domain.model.InspectionTier;
import com.agricontract.inspection.domain.model.MeasurementStatus;
import com.agricontract.inspection.domain.model.ReportCommitment;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Test-only strict JSON fixture parser; explicit tree types prevent coercion and absent/null
 * conflation.
 */
public class InspectionHashInput {
    private final ObjectMapper mapper =
            JsonMapper.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .build();

    public InspectionSettlementResultV1 result(String json) {
        JsonNode root = parse(json);
        fields(
                root,
                Set.of(
                        "resultSchemaVersion",
                        "measuredQuantityKg",
                        "acceptedQuantityKg",
                        "measurementStatus",
                        "actualQualityMetrics",
                        "inconclusiveReasons",
                        "determinedAt"));
        JsonNode metrics = root.get("actualQualityMetrics");
        fields(
                metrics,
                Set.of(
                        "commodity",
                        "type",
                        "moisturePercent",
                        "foreignMatterPercent",
                        "blackBrokenBeansPercent"));
        List<String> reasons = null;
        if (root.has("inconclusiveReasons")) {
            JsonNode values = root.get("inconclusiveReasons");
            require(values.isArray());
            reasons = new ArrayList<>();
            for (JsonNode value : values) {
                require(value.isTextual());
                reasons.add(value.textValue());
            }
        }
        return new InspectionSettlementResultV1(
                string(root, "resultSchemaVersion"),
                number(root, "measuredQuantityKg"),
                number(root, "acceptedQuantityKg"),
                MeasurementStatus.valueOf(string(root, "measurementStatus")),
                new CoffeeActualMetrics(
                        string(metrics, "commodity"),
                        CoffeeType.valueOf(string(metrics, "type")),
                        number(metrics, "moisturePercent"),
                        number(metrics, "foreignMatterPercent"),
                        number(metrics, "blackBrokenBeansPercent")),
                reasons,
                string(root, "determinedAt"));
    }

    public ReportCommitment report(String json) {
        JsonNode root = parse(json);
        fields(
                root,
                Set.of(
                        "reportId",
                        "contractId",
                        "milestoneId",
                        "tier",
                        "reportFileHash",
                        "resultHash",
                        "sourceTimestamp",
                        "actorOrSourceIdentity"));
        return new ReportCommitment(
                string(root, "reportId"),
                string(root, "contractId"),
                string(root, "milestoneId"),
                InspectionTier.valueOf(string(root, "tier")),
                string(root, "reportFileHash"),
                string(root, "resultHash"),
                string(root, "sourceTimestamp"),
                string(root, "actorOrSourceIdentity"));
    }

    private JsonNode parse(String json) {
        try {
            JsonNode node = mapper.readTree(json);
            require(node != null && node.isObject());
            return node;
        } catch (IOException | IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Invalid inspection hash input");
        }
    }

    private static void fields(JsonNode node, Set<String> allowed) {
        require(node != null && node.isObject());
        node.fieldNames().forEachRemaining(key -> require(allowed.contains(key)));
    }

    private static String string(JsonNode node, String key) {
        JsonNode value = node.get(key);
        require(value != null && value.isTextual());
        return value.textValue();
    }

    private static BigDecimal number(JsonNode node, String key) {
        JsonNode value = node.get(key);
        require(value != null && value.isNumber());
        return value.decimalValue();
    }

    private static void require(boolean valid) {
        if (!valid) {
            throw new IllegalArgumentException("Inspection hash input violates schema");
        }
    }
}
