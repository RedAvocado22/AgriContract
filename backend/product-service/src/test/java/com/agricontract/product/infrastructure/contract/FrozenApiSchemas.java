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

/** Validates a body against a schema of the frozen OpenAPI contracts in the repository, never a copy. */
public final class FrozenApiSchemas {

    public static final String GOLDEN_FLOW_API = "openapi/golden-flow-api.yaml";
    public static final String SUPPLEMENTARY_API = "events/supplementary-services-api.yaml";

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final JsonSchemaFactory FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    private static final SchemaValidatorsConfig CONFIG =
            SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();

    private FrozenApiSchemas() {
    }

    public static void assertValid(String apiFile, String definition, JsonNode instance) throws IOException {
        ObjectNode api = (ObjectNode) YAML.readTree(
                Path.of(System.getProperty("contracts.directory"), apiFile).toFile());
        ObjectNode schema = YAML.createObjectNode();
        schema.set("components", api.get("components"));
        schema.put("$ref", "#/components/schemas/" + definition);
        assertThat(FACTORY.getSchema(schema, CONFIG).validate(instance)).as(apiFile + " " + definition).isEmpty();
    }
}
