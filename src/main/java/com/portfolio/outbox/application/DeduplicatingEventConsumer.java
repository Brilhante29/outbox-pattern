package com.portfolio.outbox.application;

import com.portfolio.outbox.application.port.ProcessedEventStore;
import com.portfolio.outbox.domain.CommerceEvent;

import java.time.Clock;
import java.util.List;

public final class DeduplicatingEventConsumer {
    private final ProcessedEventStore processedEvents;
    private final Clock clock;

    public DeduplicatingEventConsumer(ProcessedEventStore processedEvents, Clock clock) {
        this.processedEvents = processedEvents;
        this.clock = clock;
    }

    public Result consume(List<CommerceEvent> events) {
        int unique = 0;
        int duplicates = 0;
        for (CommerceEvent event : events) {
            if (processedEvents.recordIfFirst(event.eventId(), clock.instant())) {
                unique++;
            } else {
                duplicates++;
            }
        }
        return new Result(events.size(), unique, duplicates);
    }

    public record Result(int received, int unique, int duplicates) {
    }
}
