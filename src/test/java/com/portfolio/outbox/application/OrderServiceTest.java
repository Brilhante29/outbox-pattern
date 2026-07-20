package com.portfolio.outbox.application;

import com.portfolio.outbox.domain.OutboxStatus;
import com.portfolio.outbox.infrastructure.InMemoryOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OrderServiceTest {

    private InMemoryOutboxRepository repository;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        repository = new InMemoryOutboxRepository();
        orderService = new OrderService(repository);
    }

    @Test
    void shouldCreateOrderAndOutboxEvent() {
        String orderId = orderService.createOrder(Map.of("item", "test-product"));

        assertNotNull(orderId);
        assertEquals(1, repository.count());
        assertEquals(OutboxStatus.PENDING, repository.findPending().get(0).getStatus());
        assertEquals("order.created", repository.findPending().get(0).getEventType());
    }

    @Test
    void shouldCreateMultipleOrders() {
        orderService.createOrder(Map.of("item", "product-1"));
        orderService.createOrder(Map.of("item", "product-2"));
        orderService.createOrder(Map.of("item", "product-3"));

        assertEquals(3, repository.count());
        assertEquals(3, repository.findPending().size());
    }

    @Test
    void shouldHandleNullOrderData() {
        String orderId = orderService.createOrder(null);
        assertNotNull(orderId);
        assertEquals(1, repository.count());
    }

    @Test
    void eachOrderShouldHaveUniqueId() {
        String orderId1 = orderService.createOrder(Map.of("item", "a"));
        String orderId2 = orderService.createOrder(Map.of("item", "b"));

        assertNotEquals(orderId1, orderId2);
    }
}
