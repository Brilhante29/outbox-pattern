package com.portfolio.outbox.domain;

public record OutboxEvent(CommerceEvent event, OutboxStatus status, int attempts) {
    public OutboxEvent {
        if (event == null || status == null || attempts < 0) {
            throw new IllegalArgumentException("Invalid outbox event");
        }
    }
}
