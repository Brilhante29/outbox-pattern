package com.portfolio.outbox.application;

import com.portfolio.outbox.application.port.EventPublisher;
import com.portfolio.outbox.application.port.OutboxStore;
import com.portfolio.outbox.domain.OutboxEvent;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

public final class OutboxWorker {
    private final OutboxStore store;
    private final EventPublisher publisher;
    private final String workerId;
    private final int batchSize;
    private final Duration leaseDuration;
    private final Duration retryDelay;
    private final Clock clock;

    public OutboxWorker(
            OutboxStore store,
            EventPublisher publisher,
            String workerId,
            int batchSize,
            Duration leaseDuration,
            Duration retryDelay,
            Clock clock
    ) {
        this.store = store;
        this.publisher = publisher;
        this.workerId = workerId;
        this.batchSize = batchSize;
        this.leaseDuration = leaseDuration;
        this.retryDelay = retryDelay;
        this.clock = clock;
    }

    public BatchResult processBatch() {
        List<OutboxEvent> claimed = store.claimBatch(workerId, batchSize, leaseDuration);
        int published = 0;
        int failed = 0;

        for (OutboxEvent outboxEvent : claimed) {
            try {
                publisher.publish(outboxEvent.event());
                store.markPublished(outboxEvent.event().eventId(), workerId, clock.instant());
                published++;
            } catch (Exception exception) {
                store.markFailed(
                        outboxEvent.event().eventId(),
                        workerId,
                        clock.instant().plus(retryDelay),
                        safeMessage(exception)
                );
                failed++;
            }
        }

        return new BatchResult(claimed.size(), published, failed);
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() > 900 ? message.substring(0, 900) : message;
    }

    public record BatchResult(int claimed, int published, int failed) {
    }
}
