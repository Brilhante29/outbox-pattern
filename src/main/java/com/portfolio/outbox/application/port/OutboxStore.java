package com.portfolio.outbox.application.port;

import com.portfolio.outbox.domain.OutboxEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxStore {
    List<OutboxEvent> claimBatch(String workerId, int batchSize, Duration leaseDuration);

    void markPublished(UUID eventId, String workerId, Instant publishedAt);

    void markFailed(UUID eventId, String workerId, Instant nextAttemptAt, String error);
}
