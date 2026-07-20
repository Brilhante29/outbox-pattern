package com.portfolio.outbox.infrastructure;

import com.portfolio.outbox.domain.OutboxEvent;
import com.portfolio.outbox.domain.OutboxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryOutboxRepositoryTest {

    private InMemoryOutboxRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryOutboxRepository();
    }

    @Test
    void shouldSaveAndRetrieveEvent() {
        OutboxEvent event = createEvent(OutboxStatus.PENDING);
        repository.save(event);

        assertEquals(1, repository.count());
    }

    @Test
    void shouldFindPendingEvents() {
        repository.save(createEvent(OutboxStatus.PENDING));
        repository.save(createEvent(OutboxStatus.PUBLISHED));
        repository.save(createEvent(OutboxStatus.PENDING));

        List<OutboxEvent> pending = repository.findPending();
        assertEquals(2, pending.size());
    }

    @Test
    void shouldMarkEventAsPublished() {
        OutboxEvent event = createEvent(OutboxStatus.PENDING);
        repository.save(event);

        repository.markPublished(event.getId());

        List<OutboxEvent> pending = repository.findPending();
        assertEquals(0, pending.size());
    }

    @Test
    void shouldMarkEventAsFailed() {
        OutboxEvent event = createEvent(OutboxStatus.PENDING);
        repository.save(event);

        repository.markFailed(event.getId());

        List<OutboxEvent> pending = repository.findPending();
        assertEquals(0, pending.size());
    }

    @Test
    void shouldClearAllEvents() {
        repository.save(createEvent(OutboxStatus.PENDING));
        repository.save(createEvent(OutboxStatus.PENDING));
        assertEquals(2, repository.count());

        repository.clear();
        assertEquals(0, repository.count());
    }

    @Test
    void markPublishedShouldHandleNonExistentId() {
        repository.markPublished("non-existent");
        assertEquals(0, repository.count());
    }

    private OutboxEvent createEvent(OutboxStatus status) {
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID().toString());
        event.setAggregateType("order");
        event.setAggregateId(UUID.randomUUID().toString());
        event.setEventType("order.created");
        event.setPayload("{}");
        event.setStatus(status);
        event.setCreatedAt(Instant.now());
        return event;
    }
}
