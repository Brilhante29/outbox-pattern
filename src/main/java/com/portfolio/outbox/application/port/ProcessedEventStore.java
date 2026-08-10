package com.portfolio.outbox.application.port;

import java.time.Instant;
import java.util.UUID;

public interface ProcessedEventStore {
    boolean recordIfFirst(UUID eventId, Instant processedAt);
}
