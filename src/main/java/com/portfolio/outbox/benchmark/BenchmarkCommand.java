package com.portfolio.outbox.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.outbox.application.DeduplicatingEventConsumer;
import com.portfolio.outbox.application.OrderService;
import com.portfolio.outbox.application.OutboxWorker;
import com.portfolio.outbox.application.port.EventPublisher;
import com.portfolio.outbox.domain.CommerceEvent;
import com.portfolio.outbox.domain.OutboxStatus;
import com.portfolio.outbox.infrastructure.JdbcOutboxAdapter;
import com.portfolio.outbox.infrastructure.JsonSchemaContract;
import com.portfolio.outbox.infrastructure.KafkaEventSource;
import com.portfolio.outbox.infrastructure.KafkaMessagePublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

@Component
public class BenchmarkCommand implements ApplicationRunner {
    private static final int REPETITIONS = 3;
    private static final int CONCURRENCY = 3;
    private static final Duration LEASE = Duration.ofSeconds(10);
    private static final Duration RETRY_DELAY = Duration.ZERO;

    private final OrderService orderService;
    private final JdbcOutboxAdapter database;
    private final ObjectMapper objectMapper;
    private final ConfigurableApplicationContext context;
    private final String bootstrapServers;
    private final Path resultPath;

    public BenchmarkCommand(
            OrderService orderService,
            JdbcOutboxAdapter database,
            ObjectMapper objectMapper,
            ConfigurableApplicationContext context,
            @Value("${outbox.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${RESULT_PATH:benchmarks/results/outbox-benchmark-v2.json}") String resultPath
    ) {
        this.orderService = orderService;
        this.database = database;
        this.objectMapper = objectMapper;
        this.context = context;
        this.bootstrapServers = bootstrapServers;
        this.resultPath = Path.of(resultPath);
    }

    @Override
    public void run(ApplicationArguments arguments) throws Exception {
        List<String> command = arguments.getNonOptionArgs();
        if (command.isEmpty()) {
            return;
        }

        switch (command.getFirst()) {
            case "reset" -> {
                database.reset();
                exit(0);
            }
            case "seed-crash" -> seedAndCrash(Integer.parseInt(command.get(1)));
            case "benchmark" -> {
                JsonNode result = runBenchmark();
                writeResult(result);
                exit(0);
            }
            default -> throw new IllegalArgumentException("Unknown command: " + command.getFirst());
        }
    }

    private void seedAndCrash(int repetition) {
        String orderId = orderService.createOrder(Map.of(
                "benchmarkRun", repetition,
                "sku", "reliability-fixture-" + repetition,
                "quantity", 1
        ));
        System.out.println("Committed order and outbox event before forced JVM halt: " + orderId);
        System.out.flush();
        Runtime.getRuntime().halt(137);
    }

    private JsonNode runBenchmark() throws Exception {
        Instant startedAt = Instant.now();
        long startedNanos = System.nanoTime();
        assertCleanProvenance();

        database.reset();
        for (int repetition = 1; repetition <= REPETITIONS; repetition++) {
            runCrashSeedProcess(repetition);
        }
        require(database.orderCount() == REPETITIONS, "Committed orders did not survive producer crashes");
        require(database.outboxCount() == REPETITIONS, "Outbox events did not survive producer crashes");

        String topic = "outbox-benchmark-" + UUID.randomUUID();
        KafkaMessagePublisher.ensureTopic(bootstrapServers, topic);

        try (KafkaMessagePublisher unavailable = new KafkaMessagePublisher(
                "127.0.0.1:1", topic, objectMapper, Duration.ofSeconds(1))) {
            OutboxWorker outageWorker = worker(unavailable, "broker-outage", REPETITIONS);
            OutboxWorker.BatchResult outage = outageWorker.processBatch();
            require(outage.claimed() == REPETITIONS && outage.failed() == REPETITIONS,
                    "Broker outage did not fail every claimed event");
        }

        try (KafkaMessagePublisher kafka = new KafkaMessagePublisher(
                bootstrapServers, topic, objectMapper, Duration.ofSeconds(5))) {
            EventPublisher lostAcknowledgement = new FailOnceAfterPublishPublisher(kafka);
            runConcurrentWorkers(lostAcknowledgement, "ack-lost");
            require(database.statusCount(OutboxStatus.FAILED) == REPETITIONS,
                    "Every post-publish acknowledgement loss must remain retryable");

            runConcurrentWorkers(kafka, "retry");
        }

        require(database.statusCount(OutboxStatus.PUBLISHED) == REPETITIONS,
                "All committed events must reach PUBLISHED");

        KafkaEventSource eventSource = new KafkaEventSource(bootstrapServers, objectMapper);
        List<CommerceEvent> received = eventSource.read(topic, REPETITIONS * 2, Duration.ofSeconds(20));
        DeduplicatingEventConsumer.Result consumed = new DeduplicatingEventConsumer(database, Clock.systemUTC())
                .consume(received);

        List<JdbcOutboxAdapter.EventMeasurement> measurements = database.eventMeasurements();
        Set<UUID> uniqueReceived = received.stream().map(CommerceEvent::eventId).collect(Collectors.toSet());
        List<Double> lostSamples = measurements.stream()
                .map(measurement -> uniqueReceived.contains(measurement.eventId()) ? 0.0 : 1.0)
                .toList();
        Map<UUID, Long> deliveriesByEvent = received.stream()
                .collect(Collectors.groupingBy(CommerceEvent::eventId, Collectors.counting()));
        List<Double> duplicateSamples = measurements.stream()
                .map(measurement -> Math.max(0.0, deliveriesByEvent.getOrDefault(measurement.eventId(), 0L) - 1.0))
                .toList();
        List<Double> lagSamples = measurements.stream().map(JdbcOutboxAdapter.EventMeasurement::publishLagMs).toList();
        List<Double> retrySamples = measurements.stream().map(measurement -> (double) (measurement.attempts() - 1)).toList();

        double lostMessages = lostSamples.stream().mapToDouble(Double::doubleValue).sum();
        double duplicates = duplicateSamples.stream().mapToDouble(Double::doubleValue).sum();
        double retryCount = retrySamples.stream().mapToDouble(Double::doubleValue).sum();
        require(lostMessages == 0, "The benchmark detected lost messages");
        require(consumed.unique() == REPETITIONS && database.processedEventCount() == REPETITIONS,
                "Consumer deduplication did not preserve exactly-once side effects");
        require(consumed.duplicates() == duplicates && duplicates == REPETITIONS,
                "Expected one deliberate duplicate delivery per event");

        double durationSeconds = (System.nanoTime() - startedNanos) / 1_000_000_000.0;
        return buildResult(
                startedAt,
                durationSeconds,
                topic,
                lostSamples,
                duplicateSamples,
                lagSamples,
                retrySamples,
                consumed
        );
    }

    private void runCrashSeedProcess(int repetition) throws Exception {
        String classPath = System.getProperty("java.class.path");
        if (!classPath.endsWith(".jar")) {
            throw new IllegalStateException("Crash benchmark must run from the Docker JAR");
        }
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(java, "-jar", classPath, "seed-crash", Integer.toString(repetition))
                .inheritIO()
                .start();
        int exitCode = process.waitFor();
        require(exitCode == 137, "Crash seed process exited with " + exitCode + " instead of 137");
    }

    private void runConcurrentWorkers(EventPublisher publisher, String phase) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);
        try {
            List<Future<OutboxWorker.BatchResult>> futures = new ArrayList<>();
            for (int index = 0; index < CONCURRENCY; index++) {
                OutboxWorker worker = worker(publisher, phase + "-" + index, 1);
                futures.add(executor.submit(worker::processBatch));
            }
            for (Future<OutboxWorker.BatchResult> future : futures) {
                require(future.get().claimed() == 1, "Concurrent worker did not claim exactly one event in " + phase);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private OutboxWorker worker(EventPublisher publisher, String workerId, int batchSize) {
        return new OutboxWorker(
                database,
                publisher,
                workerId,
                batchSize,
                LEASE,
                RETRY_DELAY,
                Clock.systemUTC()
        );
    }

    private JsonNode buildResult(
            Instant startedAt,
            double durationSeconds,
            String topic,
            List<Double> lostSamples,
            List<Double> duplicateSamples,
            List<Double> lagSamples,
            List<Double> retrySamples,
            DeduplicatingEventConsumer.Result consumed
    ) throws Exception {
        List<Map<String, Object>> metrics = List.of(
                metric("lost_messages", sum(lostSamples), "messages", "target", lostSamples, 0,
                        Map.of("target", 0, "committed_events", REPETITIONS)),
                metric("duplicates", sum(duplicateSamples), "messages", "lower_is_better", duplicateSamples, 0,
                        Map.of("consumer_side_effects", consumed.unique(), "raw_deliveries", consumed.received())),
                metric("publish_lag_p95", percentile95(lagSamples), "milliseconds", "lower_is_better", lagSamples, 0,
                        Map.of("statistic", "p95", "source", "postgresql_published_at_minus_occurred_at")),
                metric("retry_count", sum(retrySamples), "retries", "lower_is_better", retrySamples, 0,
                        Map.of("broker_outage_failures", REPETITIONS, "acknowledgement_loss_failures", REPETITIONS))
        );

        Map<String, Object> workload = new LinkedHashMap<>();
        workload.put("version", "outbox-failure-v1");
        workload.put("fixture_digest", digestText("three-orders|hard-jvm-halt-after-commit|seed-v1"));
        workload.put("config_digest", digestText("postgres17.6|redpanda26.1.14|lease10s|workers3|" + topic));
        workload.put("warmup_iterations", 0);
        workload.put("measured_iterations", REPETITIONS);
        workload.put("concurrency", CONCURRENCY);

        Map<String, Object> execution = new LinkedHashMap<>();
        execution.put("command", "powershell -NoProfile -ExecutionPolicy Bypass -File tools/benchmark.ps1");
        execution.put("started_at", startedAt.toString());
        execution.put("duration_seconds", durationSeconds);
        execution.put("exit_code", 0);
        execution.put("repeat", REPETITIONS);

        Map<String, Object> environment = new LinkedHashMap<>();
        environment.put("runtime", "Java " + System.getProperty("java.version") + " / Docker Compose");
        environment.put("architecture", System.getProperty("os.arch"));
        environment.put("hardware_class", "host-dependent-container");
        environment.put("database", "PostgreSQL 17.6");
        environment.put("broker", "Redpanda 26.1.14 Kafka API");

        Map<String, Object> provenance = new LinkedHashMap<>();
        provenance.put("source_commit", requiredEnvironment("SOURCE_COMMIT"));
        provenance.put("clean_tree", true);
        provenance.put("image_ref", requiredEnvironment("IMAGE_REF"));
        provenance.put("image_digest", requiredDigestEnvironment("IMAGE_DIGEST"));
        provenance.put("dependency_lock_digest", digestFile(Path.of("gradle.lockfile")));
        provenance.put("producer", requiredEnvironment("BENCHMARK_PRODUCER"));
        String ciRunUrl = System.getenv("CI_RUN_URL");
        if (ciRunUrl != null && !ciRunUrl.isBlank()) {
            provenance.put("ci_run_url", ciRunUrl);
        }
        provenance.put("artifact_digest", digestBytes(objectMapper.writeValueAsBytes(Map.of(
                "workload", workload,
                "metrics", metrics,
                "execution", execution
        ))));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schema_version", 2);
        result.put("run_id", UUID.randomUUID().toString());
        result.put("project", "outbox-pattern");
        result.put("benchmark_id", "transactional-outbox-failure-recovery");
        result.put("workload", workload);
        result.put("metrics", metrics);
        result.put("execution", execution);
        result.put("environment", environment);
        result.put("provenance", provenance);
        result.put("comparability_key", "outbox-pattern:postgres17-redpanda26:failure-v1");
        return objectMapper.valueToTree(result);
    }

    private Map<String, Object> metric(
            String name,
            double value,
            String unit,
            String direction,
            List<Double> samples,
            int failures,
            Map<String, Object> summary
    ) {
        Map<String, Object> metric = new LinkedHashMap<>();
        metric.put("name", name);
        metric.put("value", value);
        metric.put("unit", unit);
        metric.put("direction", direction);
        metric.put("samples", samples);
        metric.put("failures", failures);
        metric.put("summary", summary);
        return metric;
    }

    private void writeResult(JsonNode result) throws IOException {
        new JsonSchemaContract(Path.of(".portfolio", "contracts", "benchmark-result-v2.schema.json"))
                .validate(result);
        Files.createDirectories(resultPath.toAbsolutePath().getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(resultPath.toFile(), result);
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        System.out.println("Benchmark result written to " + resultPath.toAbsolutePath());
    }

    private void assertCleanProvenance() {
        require("true".equalsIgnoreCase(requiredEnvironment("SOURCE_TREE_CLEAN")),
                "Benchmark provenance requires a clean source tree");
        require(requiredEnvironment("SOURCE_COMMIT").matches("[0-9a-f]{40}"),
                "SOURCE_COMMIT must be a full Git SHA");
    }

    private String requiredDigestEnvironment(String name) {
        String value = requiredEnvironment(name);
        require(value.matches("sha256:[0-9a-f]{64}"), name + " must be a sha256 digest");
        return value;
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }

    private String digestFile(Path path) throws IOException {
        return digestBytes(Files.readAllBytes(path));
    }

    private String digestText(String value) {
        return digestBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private String digestBytes(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder hex = new StringBuilder();
            for (byte item : digest) {
                hex.append(String.format("%02x", item));
            }
            return "sha256:" + hex;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not calculate SHA-256", exception);
        }
    }

    private double percentile95(List<Double> samples) {
        List<Double> sorted = samples.stream().sorted(Comparator.naturalOrder()).toList();
        int index = Math.max(0, (int) Math.ceil(sorted.size() * 0.95) - 1);
        return sorted.get(index);
    }

    private double sum(List<Double> samples) {
        return samples.stream().mapToDouble(Double::doubleValue).sum();
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private void exit(int code) {
        int applicationCode = SpringApplication.exit(context, () -> code);
        System.exit(applicationCode);
    }

    private static final class FailOnceAfterPublishPublisher implements EventPublisher {
        private final EventPublisher delegate;
        private final Set<UUID> failedAcknowledgements = ConcurrentHashMap.newKeySet();

        private FailOnceAfterPublishPublisher(EventPublisher delegate) {
            this.delegate = delegate;
        }

        @Override
        public void publish(CommerceEvent event) throws Exception {
            delegate.publish(event);
            if (failedAcknowledgements.add(event.eventId())) {
                throw new IOException("Injected acknowledgement loss after broker publish");
            }
        }
    }
}
