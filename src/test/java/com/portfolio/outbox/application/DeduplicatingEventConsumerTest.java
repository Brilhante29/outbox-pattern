package com.portfolio.outbox.application;

import com.portfolio.outbox.domain.CommerceEvent;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeduplicatingEventConsumerTest {
    @Test
    void appliesSideEffectOncePerEventId() {
        Set<UUID> processed = new HashSet<>();
        Instant now = Instant.parse("2026-08-10T12:00:00Z");
        UUID eventId = UUID.randomUUID();
        CommerceEvent event = new CommerceEvent(
                eventId, "order.created", 1, UUID.randomUUID(), null, UUID.randomUUID(), null, now, Map.of()
        );
        DeduplicatingEventConsumer consumer = new DeduplicatingEventConsumer(
                (id, ignored) -> processed.add(id),
                Clock.fixed(now, ZoneOffset.UTC)
        );

        DeduplicatingEventConsumer.Result result = consumer.consume(java.util.List.of(event, event));

        assertEquals(new DeduplicatingEventConsumer.Result(2, 1, 1), result);
    }
}
