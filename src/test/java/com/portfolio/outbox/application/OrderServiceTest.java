package com.portfolio.outbox.application;

import com.portfolio.outbox.application.port.OrderTransaction;
import com.portfolio.outbox.domain.CommerceEvent;
import com.portfolio.outbox.domain.Order;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OrderServiceTest {
    @Test
    void delegatesOrderAndEventToOneTransactionBoundary() {
        AtomicReference<Order> savedOrder = new AtomicReference<>();
        AtomicReference<CommerceEvent> savedEvent = new AtomicReference<>();
        OrderTransaction transaction = (order, event) -> {
            savedOrder.set(order);
            savedEvent.set(event);
        };

        String orderId = new OrderService(transaction).createOrder(Map.of("sku", "book", "quantity", 1));

        assertEquals(orderId, savedOrder.get().id().toString());
        assertEquals(savedOrder.get().id(), savedEvent.get().aggregateId());
        assertEquals("order.created", savedEvent.get().eventType());
        assertEquals(1, savedEvent.get().eventVersion());
        assertNotNull(savedEvent.get().correlationId());
    }
}
