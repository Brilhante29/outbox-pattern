package com.portfolio.outbox.application.port;

import com.portfolio.outbox.domain.CommerceEvent;

public interface EventPublisher extends AutoCloseable {
    void publish(CommerceEvent event) throws Exception;

    @Override
    default void close() {
    }
}
