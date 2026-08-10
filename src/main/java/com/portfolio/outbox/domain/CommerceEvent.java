package com.portfolio.outbox.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record CommerceEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        UUID aggregateId,
        UUID sagaId,
        UUID correlationId,
        UUID causationId,
        Instant occurredAt,
        Map<String, Object> payload
) {
    public CommerceEvent {
        if (eventId == null || aggregateId == null || correlationId == null || occurredAt == null) {
            throw new IllegalArgumentException("Event identity and timestamps are required");
        }
        if (eventType == null || eventType.isBlank() || eventVersion < 1) {
            throw new IllegalArgumentException("Event type and version are required");
        }
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
