package com.portfolio.outbox.application;

import com.portfolio.outbox.domain.OutboxEvent;
import com.portfolio.outbox.domain.OutboxRepository;
import com.portfolio.outbox.domain.OutboxStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderService {
    private final OutboxRepository outboxRepository;

    public OrderService(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    public String createOrder(Map<String, Object> orderData) {
        String orderId = UUID.randomUUID().toString();

        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID().toString());
        event.setAggregateType("order");
        event.setAggregateId(orderId);
        event.setEventType("order.created");
        event.setPayload(orderData != null ? orderData.toString() : "{}");
        event.setStatus(OutboxStatus.PENDING);
        event.setCreatedAt(Instant.now());

        outboxRepository.save(event);

        return orderId;
    }
}
