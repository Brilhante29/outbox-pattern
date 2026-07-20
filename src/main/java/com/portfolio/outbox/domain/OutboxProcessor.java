package com.portfolio.outbox.domain;

import java.util.List;

public class OutboxProcessor {
    private final OutboxRepository repository;
    private final MessagePublisher publisher;

    public OutboxProcessor(OutboxRepository repository, MessagePublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    public int processPending() {
        List<OutboxEvent> pending = repository.findPending();
        for (OutboxEvent event : pending) {
            try {
                publisher.publish(event);
                repository.markPublished(event.getId());
            } catch (Exception e) {
                repository.markFailed(event.getId());
            }
        }
        return pending.size();
    }
}
