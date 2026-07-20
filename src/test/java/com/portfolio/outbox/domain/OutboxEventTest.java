package com.portfolio.outbox.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OutboxEventTest {

    @Test
    void shouldCreateOutboxEventWithDefaultConstructor() {
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID().toString());
        event.setAggregateType("order");
        event.setAggregateId("123");
        event.setEventType("order.created");
        event.setPayload("{\"item\":\"test\"}");
        event.setStatus(OutboxStatus.PENDING);
        event.setCreatedAt(Instant.now());

        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertNotNull(event.getId());
        assertEquals("order.created", event.getEventType());
    }

    @Test
    void shouldCreateOutboxEventWithParameterizedConstructor() {
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        OutboxEvent event = new OutboxEvent(id, "order", "456", "order.shipped", "{}", OutboxStatus.PENDING, now);

        assertEquals(id, event.getId());
        assertEquals("order", event.getAggregateType());
        assertEquals("456", event.getAggregateId());
        assertEquals("order.shipped", event.getEventType());
        assertEquals("{}", event.getPayload());
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertEquals(now, event.getCreatedAt());
    }

    @Test
    void statusShouldTransitionToPublished() {
        OutboxEvent event = new OutboxEvent();
        event.setStatus(OutboxStatus.PENDING);
        event.setStatus(OutboxStatus.PUBLISHED);
        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
    }

    @Test
    void statusShouldTransitionToFailed() {
        OutboxEvent event = new OutboxEvent();
        event.setStatus(OutboxStatus.PENDING);
        event.setStatus(OutboxStatus.FAILED);
        assertEquals(OutboxStatus.FAILED, event.getStatus());
    }

    @Test
    void toStringShouldNotBeNull() {
        OutboxEvent event = new OutboxEvent();
        event.setId("test-id");
        event.setEventType("test.event");
        event.setStatus(OutboxStatus.PENDING);
        assertNotNull(event.toString());
        assertTrue(event.toString().contains("test-id"));
    }
}
