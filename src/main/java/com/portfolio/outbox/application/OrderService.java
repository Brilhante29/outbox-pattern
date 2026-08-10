package com.portfolio.outbox.application;

import com.portfolio.outbox.application.port.OrderTransaction;
import com.portfolio.outbox.domain.CommerceEvent;
import com.portfolio.outbox.domain.Order;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderService {
    private final OrderTransaction transaction;

    public OrderService(OrderTransaction transaction) {
        this.transaction = transaction;
    }

    public String createOrder(Map<String, Object> orderData) {
        Instant now = Instant.now();
        UUID orderId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        Map<String, Object> payload = orderData == null ? Map.of() : Map.copyOf(orderData);
        Order order = new Order(orderId, payload, now);
        CommerceEvent event = new CommerceEvent(
                UUID.randomUUID(),
                "order.created",
                1,
                orderId,
                null,
                correlationId,
                null,
                now,
                payload
        );

        transaction.persist(order, event);
        return orderId.toString();
    }
}
