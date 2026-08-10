package com.portfolio.outbox.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.portfolio.outbox.domain.CommerceEvent;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

class CommerceEventContractTest {
    @Test
    void serializedEventMatchesVersionedContract() {
        CommerceEvent event = new CommerceEvent(
                UUID.randomUUID(),
                "order.created",
                1,
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                null,
                Instant.parse("2026-08-10T12:00:00Z"),
                Map.of("sku", "book")
        );

        ObjectMapper mapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        new JsonSchemaContract(Path.of("contracts", "commerce-event-v1.schema.json"))
                .validate(mapper.valueToTree(event));
    }
}
