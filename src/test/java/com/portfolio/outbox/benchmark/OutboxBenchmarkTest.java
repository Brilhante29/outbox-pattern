package com.portfolio.outbox.benchmark;

import com.portfolio.outbox.infrastructure.InMemoryMessagePublisher;
import com.portfolio.outbox.infrastructure.InMemoryOutboxRepository;
import com.portfolio.outbox.infrastructure.SimulatedFailureInjector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OutboxBenchmarkTest {

    private InMemoryOutboxRepository repository;
    private InMemoryMessagePublisher publisher;
    private SimulatedFailureInjector failureInjector;
    private OutboxBenchmark benchmark;

    @BeforeEach
    void setUp() {
        repository = new InMemoryOutboxRepository();
        failureInjector = new SimulatedFailureInjector(42);
        publisher = new InMemoryMessagePublisher(failureInjector);
        benchmark = new OutboxBenchmark(repository, publisher, failureInjector);
    }

    @Test
    void benchmarkShouldResultInZeroLostMessages() {
        BenchmarkResult result = benchmark.runBenchmark();

        assertEquals(0.0, result.getValue(), 0.001,
                "Outbox pattern should recover all messages under simulated failure");
        assertNotNull(result.getMetric());
        assertEquals("lost_messages_under_failure", result.getMetric());
        assertNotNull(result.getTimestamp());
    }

    @Test
    void benchmarkShouldProduceAllRequiredFields() {
        BenchmarkResult result = benchmark.runBenchmark();

        assertEquals("outbox-pattern", result.getProject());
        assertEquals("messages", result.getUnit());
        assertNotNull(result.getParameters());
        assertNotNull(result.getEnvironment());
        assertNotNull(result.getCommand());
    }

    @Test
    void processorShouldRecoverAllPendingEvents() {
        failureInjector.configure(1.0);

        com.portfolio.outbox.domain.OutboxProcessor processor =
                new com.portfolio.outbox.domain.OutboxProcessor(repository, publisher);

        int saved = repository.count();

        int processed = processor.processPending();
        int published = publisher.count();

        assertEquals(saved, published);
        assertEquals(0, repository.findPending().size());
    }
}
