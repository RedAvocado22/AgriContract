package com.agricontract.user.infrastructure.contract;

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

/** Validates against the frozen contracts in the repository, never a copy. Same approach as bank-service. */
public final class FrozenSchemas {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final JsonSchemaFactory FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    private static final SchemaValidatorsConfig CONFIG =
            SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();

    private FrozenSchemas() {
    }

    public static void assertNotificationCommand(String definition, JsonNode instance) throws IOException {
        ObjectNode schema = (ObjectNode) YAML.readTree(
                Path.of(System.getProperty("contracts.directory"), "events/notification-commands.yaml").toFile());
        schema.remove("oneOf");
        schema.put("$ref", "#/$defs/" + definition);
        assertThat(FACTORY.getSchema(schema, CONFIG).validate(instance)).as(definition).isEmpty();
    }
}
