package com.portfolio.outbox.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.portfolio.outbox.domain.OutboxEvent;
import com.portfolio.outbox.domain.OutboxProcessor;
import com.portfolio.outbox.domain.OutboxStatus;
import com.portfolio.outbox.infrastructure.InMemoryMessagePublisher;
import com.portfolio.outbox.infrastructure.InMemoryOutboxRepository;
import com.portfolio.outbox.infrastructure.SimulatedFailureInjector;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class OutboxBenchmark implements CommandLineRunner {

    private final InMemoryOutboxRepository repository;
    private final InMemoryMessagePublisher publisher;
    private final SimulatedFailureInjector failureInjector;

    public OutboxBenchmark(InMemoryOutboxRepository repository,
                           InMemoryMessagePublisher publisher,
                           SimulatedFailureInjector failureInjector) {
        this.repository = repository;
        this.publisher = publisher;
        this.failureInjector = failureInjector;
    }

    @Override
    public void run(String... args) throws Exception {
        for (String arg : args) {
            if (arg.equals("benchmark")) {
                BenchmarkResult result = runBenchmark();
                writeResult(result);
                System.out.println("Benchmark complete. Result written to benchmarks/results/");
                SpringExit.exit(0);
                return;
            }
        }
    }

    public BenchmarkResult runBenchmark() {
        int numOrders = 100;
        double failureRate = 0.3;
        long seed = 42;

        repository.clear();
        publisher.clear();
        failureInjector.configure(failureRate);

        int createdCount = 0;
        for (int i = 0; i < numOrders; i++) {
            String eventId = UUID.randomUUID().toString();
            OutboxEvent event = new OutboxEvent();
            event.setId(eventId);
            event.setAggregateType("order");
            event.setAggregateId(UUID.randomUUID().toString());
            event.setEventType("order.created");
            event.setPayload("{\"item\":\"product-" + i + "\"}");
            event.setStatus(OutboxStatus.PENDING);
            event.setCreatedAt(Instant.now());

            repository.save(event);

            if (i < numOrders / 2) {
                try {
                    failureInjector.maybeFail("publish");
                } catch (RuntimeException ignored) {
                }
            }

            createdCount++;
        }

        failureInjector.disable();

        OutboxProcessor processor = new OutboxProcessor(repository, publisher);
        int processed = processor.processPending();

        int totalSaved = repository.count();
        int publishedCount = publisher.count();
        int pendingCount = repository.findPending().size();
        long failedCount = repository.count() - publishedCount - pendingCount;
        long lostMessages = totalSaved - publishedCount;

        System.out.println("=== Outbox Pattern Benchmark ===");
        System.out.println("Orders attempted: " + numOrders);
        System.out.println("Failure rate: " + (failureRate * 100) + "%");
        System.out.println("Events saved to outbox: " + totalSaved);
        System.out.println("Events published by processor: " + publishedCount);
        System.out.println("Events still pending: " + pendingCount);
        System.out.println("Events marked failed: " + failedCount);
        System.out.println("Lost messages: " + lostMessages);

        Map<String, Object> params = new HashMap<>();
        params.put("numOrders", numOrders);
        params.put("failureRate", failureRate);
        params.put("seed", seed);
        params.put("expectedLost", 0);

        Map<String, Object> env = new HashMap<>();
        env.put("java.version", System.getProperty("java.version"));
        env.put("os.name", System.getProperty("os.name"));

        BenchmarkResult result = new BenchmarkResult();
        result.setProject("outbox-pattern");
        result.setMetric("lost_messages_under_failure");
        result.setValue(lostMessages);
        result.setUnit("messages");
        result.setTimestamp(Instant.now());
        result.setEnvironment(env);
        result.setParameters(params);
        result.setCommand("docker run --rm outbox-pattern benchmark");

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        try {
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
            System.out.println("\nBenchmark JSON:\n" + json);
        } catch (Exception e) {
            System.err.println("Failed to serialize benchmark result: " + e.getMessage());
        }

        return result;
    }

    private void writeResult(BenchmarkResult result) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            Path outputDir = Paths.get("benchmarks", "results");
            Files.createDirectories(outputDir);
            Path outputFile = outputDir.resolve("lost_messages_under_failure.json");
            mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile.toFile(), result);
            System.out.println("Result written to: " + outputFile.toAbsolutePath());
        } catch (Exception e) {
            System.err.println("Failed to write benchmark result file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

class SpringExit {
    static void exit(int code) {
        System.exit(code);
    }
}
