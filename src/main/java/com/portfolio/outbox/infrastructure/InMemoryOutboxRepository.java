package com.portfolio.outbox.infrastructure;

import com.portfolio.outbox.domain.OutboxEvent;
import com.portfolio.outbox.domain.OutboxRepository;
import com.portfolio.outbox.domain.OutboxStatus;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class InMemoryOutboxRepository implements OutboxRepository {
    private final Map<String, OutboxEvent> store = new ConcurrentHashMap<>();

    @Override
    public void save(OutboxEvent event) {
        store.put(event.getId(), event);
    }

    @Override
    public void markPublished(String id) {
        OutboxEvent event = store.get(id);
        if (event != null) {
            event.setStatus(OutboxStatus.PUBLISHED);
        }
    }

    @Override
    public void markFailed(String id) {
        OutboxEvent event = store.get(id);
        if (event != null) {
            event.setStatus(OutboxStatus.FAILED);
        }
    }

    @Override
    public List<OutboxEvent> findPending() {
        return store.values().stream()
                .filter(e -> e.getStatus() == OutboxStatus.PENDING)
                .collect(Collectors.toList());
    }

    public void clear() {
        store.clear();
    }

    public int count() {
        return store.size();
    }
}
