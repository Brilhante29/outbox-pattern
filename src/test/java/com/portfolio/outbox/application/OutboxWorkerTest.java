package com.portfolio.outbox.application;

import com.portfolio.outbox.application.port.EventPublisher;
import com.portfolio.outbox.application.port.OutboxStore;
import com.portfolio.outbox.domain.CommerceEvent;
import com.portfolio.outbox.domain.OutboxEvent;
import com.portfolio.outbox.domain.OutboxStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OutboxWorkerTest {
    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");

    @Test
    void marksPublishedOnlyAfterPublisherSucceeds() {
        RecordingStore store = new RecordingStore(event());
        EventPublisher publisher = ignored -> {
        };

        OutboxWorker.BatchResult result = worker(store, publisher).processBatch();

        assertEquals(new OutboxWorker.BatchResult(1, 1, 0), result);
        assertEquals(List.of("published"), store.transitions);
    }

    @Test
    void failedPublishReturnsEventToRetryableState() {
        RecordingStore store = new RecordingStore(event());
        EventPublisher publisher = ignored -> {
            throw new IllegalStateException("broker unavailable");
        };

        OutboxWorker.BatchResult result = worker(store, publisher).processBatch();

        assertEquals(new OutboxWorker.BatchResult(1, 0, 1), result);
        assertEquals(List.of("failed:broker unavailable"), store.transitions);
    }

    private OutboxWorker worker(RecordingStore store, EventPublisher publisher) {
        return new OutboxWorker(
                store,
                publisher,
                "worker-1",
                10,
                Duration.ofSeconds(5),
                Duration.ZERO,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private OutboxEvent event() {
        UUID id = UUID.randomUUID();
        return new OutboxEvent(new CommerceEvent(
                id,
                "order.created",
                1,
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                null,
                NOW,
                Map.of("sku", "book")
        ), OutboxStatus.PENDING, 0);
    }

    private static final class RecordingStore implements OutboxStore {
        private final OutboxEvent event;
        private final List<String> transitions = new ArrayList<>();

        private RecordingStore(OutboxEvent event) {
            this.event = event;
        }

        @Override
        public List<OutboxEvent> claimBatch(String workerId, int batchSize, Duration leaseDuration) {
            return List.of(event);
        }

        @Override
        public void markPublished(UUID eventId, String workerId, Instant publishedAt) {
            transitions.add("published");
        }

        @Override
        public void markFailed(UUID eventId, String workerId, Instant nextAttemptAt, String error) {
            transitions.add("failed:" + error);
        }
    }
}
