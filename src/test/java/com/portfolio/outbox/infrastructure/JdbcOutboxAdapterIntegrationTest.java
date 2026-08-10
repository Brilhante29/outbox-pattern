package com.portfolio.outbox.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.outbox.domain.CommerceEvent;
import com.portfolio.outbox.domain.Order;
import com.portfolio.outbox.domain.OutboxStatus;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Testcontainers
class JdbcOutboxAdapterIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-alpine");

    private static JdbcOutboxAdapter adapter;

    @BeforeAll
    static void configureDatabase() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        adapter = new JdbcOutboxAdapter(dataSource, new ObjectMapper().findAndRegisterModules());
    }

    @BeforeEach
    void reset() {
        adapter.reset();
    }

    @Test
    void storesOrderAndEventAtomically() {
        Fixture first = fixture();
        adapter.persist(first.order(), first.event());

        Fixture conflictingEvent = fixtureWithEventId(first.event().eventId());
        assertThrows(RuntimeException.class, () -> adapter.persist(conflictingEvent.order(), conflictingEvent.event()));

        assertEquals(1, adapter.orderCount());
        assertEquals(1, adapter.outboxCount());
    }

    @Test
    void concurrentWorkersClaimDifferentRowsAndFailedRowsAreRetryable() throws Exception {
        for (int index = 0; index < 3; index++) {
            Fixture fixture = fixture();
            adapter.persist(fixture.order(), fixture.event());
        }

        UUID failedId;
        try (var executor = Executors.newFixedThreadPool(3)) {
            List<Callable<UUID>> claims = List.of(
                    () -> adapter.claimBatch("worker-1", 1, Duration.ofSeconds(5)).getFirst().event().eventId(),
                    () -> adapter.claimBatch("worker-2", 1, Duration.ofSeconds(5)).getFirst().event().eventId(),
                    () -> adapter.claimBatch("worker-3", 1, Duration.ofSeconds(5)).getFirst().event().eventId()
            );
            var futures = executor.invokeAll(claims);
            failedId = futures.getFirst().get();
            Set<UUID> claimed = new HashSet<>();
            for (var future : futures) {
                claimed.add(future.get());
            }
            assertEquals(3, claimed.size());
        }

        adapter.markFailed(failedId, "worker-1", Instant.now(), "broker unavailable");
        assertTrue(adapter.claimBatch("retry-worker", 1, Duration.ofSeconds(5)).stream()
                .anyMatch(event -> event.event().eventId().equals(failedId)));
    }

    @Test
    void processedEventPrimaryKeyDeduplicatesConsumerSideEffects() {
        UUID eventId = UUID.randomUUID();
        assertTrue(adapter.recordIfFirst(eventId, Instant.now()));
        assertEquals(false, adapter.recordIfFirst(eventId, Instant.now()));
        assertEquals(1, adapter.processedEventCount());
    }

    private static Fixture fixture() {
        return fixtureWithEventId(UUID.randomUUID());
    }

    private static Fixture fixtureWithEventId(UUID eventId) {
        Instant now = Instant.now();
        UUID orderId = UUID.randomUUID();
        Order order = new Order(orderId, Map.of("sku", "book"), now);
        CommerceEvent event = new CommerceEvent(
                eventId, "order.created", 1, orderId, null, UUID.randomUUID(), null, now, Map.of("sku", "book")
        );
        return new Fixture(order, event);
    }

    private record Fixture(Order order, CommerceEvent event) {
    }
}
