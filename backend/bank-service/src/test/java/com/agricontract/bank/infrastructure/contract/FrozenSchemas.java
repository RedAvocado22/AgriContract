package com.agricontract.bank.infrastructure.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Validates against the frozen contracts in the repository, never a copy. Same approach as reputation-service. */
public final class FrozenSchemas {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final JsonSchemaFactory FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    private static final SchemaValidatorsConfig CONFIG =
            SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();

    private FrozenSchemas() {
    }

    public static void assertEvent(String definition, JsonNode instance) throws IOException {
        ObjectNode schema = read("events/golden-flow-events.yaml");
        // Resolve the external envelope locally: no network access, no copied contract.
        ObjectNode envelope = read("events/event-envelope.yaml");
        envelope.remove("$id");
        ((ObjectNode) envelope.path("properties").path("subject")).put("$ref", "#/$defs/envelope/$defs/subject");
        ((ObjectNode) schema.get("$defs")).set("envelope", envelope);
        replaceEnvelopeRef(schema);
        schema.remove("oneOf");
        schema.put("$ref", "#/$defs/" + definition);
        assertThat(FACTORY.getSchema(schema, CONFIG).validate(instance)).as(definition).isEmpty();
    }

    private static ObjectNode read(String file) throws IOException {
        return (ObjectNode) YAML.readTree(Path.of(System.getProperty("contracts.directory"), file).toFile());
    }

    private static void replaceEnvelopeRef(JsonNode node) {
        if (node.isObject() && "./event-envelope.yaml".equals(node.path("$ref").asText())) {
            ((ObjectNode) node).put("$ref", "#/$defs/envelope");
        }
        node.elements().forEachRemaining(FrozenSchemas::replaceEnvelopeRef);
    }
}
