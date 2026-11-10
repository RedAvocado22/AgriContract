package com.agricontract.product.infrastructure.contract;

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

/** Validates against the frozen contracts in the repository, never a copy. Same approach as the other services. */
public final class FrozenSchemas {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final JsonSchemaFactory FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    private static final SchemaValidatorsConfig CONFIG =
            SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();

    public static final String GOLDEN_FLOW_API = "openapi/golden-flow-api.yaml";
    public static final String SUPPLEMENTARY_API = "events/supplementary-services-api.yaml";

    private FrozenSchemas() {
    }

    /** Validates a body against {@code components.schemas.<definition>} of a frozen OpenAPI file. */
    public static void assertApiSchema(String apiFile, String definition, JsonNode instance) throws IOException {
        ObjectNode api = read(apiFile);
        ObjectNode schema = YAML.createObjectNode();
        schema.set("components", api.get("components"));
        schema.put("$ref", "#/components/schemas/" + definition);
        assertThat(FACTORY.getSchema(schema, CONFIG).validate(instance)).as(apiFile + " " + definition).isEmpty();
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
