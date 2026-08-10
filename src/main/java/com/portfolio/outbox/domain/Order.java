package com.portfolio.outbox.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record Order(UUID id, Map<String, Object> payload, Instant createdAt) {
    public Order {
        if (id == null || createdAt == null) {
            throw new IllegalArgumentException("Order identity and creation time are required");
        }
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
