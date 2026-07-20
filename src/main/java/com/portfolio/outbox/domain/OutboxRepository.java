package com.portfolio.outbox.domain;

import java.util.List;

public interface OutboxRepository {
    void save(OutboxEvent event);
    void markPublished(String id);
    void markFailed(String id);
    List<OutboxEvent> findPending();
}
