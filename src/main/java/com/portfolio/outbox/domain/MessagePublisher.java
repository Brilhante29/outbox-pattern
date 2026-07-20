package com.portfolio.outbox.domain;

public interface MessagePublisher {
    void publish(OutboxEvent event);
}
