package com.agricontract.inspection.infrastructure.hashing;

import static org.assertj.core.api.Assertions.assertThat;

import com.agricontract.inspection.domain.service.InspectionHashes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

/** Reads frozen files in place; no duplicated schemas or production event publisher. */
class InspectionHashContractTest {
    final ObjectMapper json = new ObjectMapper();
    final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
    final InspectionHashInput input = new InspectionHashInput();
    final JcsCanonicalizer jcs = new JcsCanonicalizer();
    final InspectionHashes hashes = new InspectionHashes(jcs);
    final JsonSchemaFactory factory =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    final SchemaValidatorsConfig config =
            SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();

    ObjectNode read(String file) throws Exception {
        return (ObjectNode)
                yaml.readTree(
                        Path.of(System.getProperty("contracts.directory"), "events", file)
                                .toFile());
    }

    JsonSchema resultSchema(boolean api) throws Exception {
        ObjectNode root = read(api ? "supplementary-services-api.yaml" : "golden-flow-events.yaml");
        root.remove("oneOf");
        root.put(
                "$ref",
                api
                        ? "#/components/schemas/InspectionSettlementResultV1"
                        : "#/$defs/inspectionSettlementResultV1");
        return factory.getSchema(root, config);
    }

    @Test
    void generatedResultSatisfiesBothFrozenSchemasAndRejectsPolicyFields() throws Exception {
        JsonNode result = json.readTree(jcs.canonicalize(input.result(InspectionHashTest.RESULT)));
        for (boolean api : new boolean[] {true, false}) {
            var schema = resultSchema(api);
            assertThat(schema.validate(result)).isEmpty();
            ObjectNode subjective = result.deepCopy();
            subjective.put("qualityDisposition", "CONFORMING");
            assertThat(schema.validate(subjective)).isNotEmpty();
            ObjectNode nullReasons = result.deepCopy();
            nullReasons.putNull("inconclusiveReasons");
            assertThat(schema.validate(nullReasons)).isNotEmpty();
            ObjectNode incomplete = result.deepCopy();
            incomplete.put("measurementStatus", "INCONCLUSIVE");
            assertThat(schema.validate(incomplete)).isNotEmpty();
        }
    }

    @Test
    void reportHashSourceTimestampTakesConfirmedAtValueWithoutRenamingProjectionKey()
            throws Exception {
        ObjectNode commitment = (ObjectNode) json.readTree(InspectionHashTest.REPORT);
        var result = input.result(InspectionHashTest.RESULT);
        commitment.put("resultHash", hashes.calculateResultHash(result));
        String reportHash = hashes.calculateReportHash(input.report(commitment.toString()));
        ObjectNode payload = commitment.deepCopy();
        payload.put("confirmedAt", payload.remove("sourceTimestamp").textValue());
        payload.put("inspectorId", payload.remove("actorOrSourceIdentity").textValue());
        payload.put("reportHash", reportHash);
        payload.set("normalizedResult", json.readTree(jcs.canonicalize(result)));
        ObjectNode root = read("golden-flow-events.yaml");
        JsonNode frozenPayload =
                root.path("$defs")
                        .path("inspectionReportConfirmed")
                        .path("allOf")
                        .get(1)
                        .path("properties")
                        .path("payload");
        ObjectNode schema = (ObjectNode) frozenPayload.deepCopy();
        schema.set("$defs", root.get("$defs"));
        assertThat(factory.getSchema(schema, config).validate(payload)).isEmpty();

        // Simulate transport serialization: reconstruct only the approved eight-field projection.
        ObjectNode received = (ObjectNode) json.readTree(json.writeValueAsBytes(payload));
        hashes.verifyResultHash(
                input.result(received.remove("normalizedResult").toString()),
                received.get("resultHash").textValue());
        String expected = received.remove("reportHash").textValue();
        received.put("sourceTimestamp", received.remove("confirmedAt").textValue());
        received.put("actorOrSourceIdentity", received.remove("inspectorId").textValue());
        assertThat(received.properties())
                .extracting(Map.Entry::getKey)
                .containsExactlyInAnyOrder(
                        "reportId",
                        "contractId",
                        "milestoneId",
                        "tier",
                        "reportFileHash",
                        "resultHash",
                        "sourceTimestamp",
                        "actorOrSourceIdentity");
        assertThat(received.has("confirmedAt")).isFalse();
        assertThat(received.get("sourceTimestamp")).isEqualTo(payload.get("confirmedAt"));
        assertThat(received.get("tier").textValue()).isEqualTo("LEVEL_1_5");
        hashes.verifyReportHash(input.report(received.toString()), expected);
    }
}
