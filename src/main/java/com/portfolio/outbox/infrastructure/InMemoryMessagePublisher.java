package com.portfolio.outbox.infrastructure;

import com.portfolio.outbox.domain.MessagePublisher;
import com.portfolio.outbox.domain.OutboxEvent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class InMemoryMessagePublisher implements MessagePublisher {
    private final List<OutboxEvent> published = new CopyOnWriteArrayList<>();
    private final SimulatedFailureInjector failureInjector;

    public InMemoryMessagePublisher(SimulatedFailureInjector failureInjector) {
        this.failureInjector = failureInjector;
    }

    @Override
    public void publish(OutboxEvent event) {
        failureInjector.maybeFail("publish");
        published.add(event);
    }

    public List<OutboxEvent> getPublished() {
        return published;
    }

    public void clear() {
        published.clear();
    }

    public int count() {
        return published.size();
    }
}
