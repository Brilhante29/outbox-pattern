package com.portfolio.outbox.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

public final class JsonSchemaContract {
    private final JsonSchema schema;

    public JsonSchemaContract(Path schemaPath) {
        try {
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
            this.schema = factory.getSchema(schemaPath.toUri());
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load JSON schema " + schemaPath, exception);
        }
    }

    public void validate(JsonNode value) {
        Set<ValidationMessage> errors = schema.validate(value);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("JSON contract violation: " + errors);
        }
    }
}
