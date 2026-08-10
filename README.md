# #20 outbox-pattern

**Result:** **0 lost messages** across 3 forced JVM crashes, with **3 duplicate deliveries safely deduplicated** and **6 retries**.

**Proves:** a PostgreSQL transactional outbox survives a process crash after commit, retries a Kafka-compatible broker outage, and keeps consumer side effects idempotent under at-least-once delivery.

## Reproduce

Requirements: Docker Desktop (or Docker Engine with Compose) and PowerShell 7.

```powershell
./tools/benchmark.ps1
```

The command starts PostgreSQL and Redpanda, builds the application, hard-stops three producer JVMs with exit code `137` after each database commit, injects an unavailable Kafka endpoint, retries with three concurrent workers, and writes a fresh host artifact to `benchmarks/results/outbox-benchmark-v2.json`. It refuses a dirty Git tree so the evidence provenance remains truthful.

## Benchmark

| Metric | Result | Meaning |
|---|---:|---|
| `lost_messages` | **0** | Every committed outbox event reached the consumer |
| `duplicates` | **3** | Deliberate post-publish acknowledgement losses produced one duplicate per event |
| `retry_count` | **6** | One broker-outage retry and one acknowledgement-loss retry per event |
| `publish_lag_p95` | **14,308.564 ms** | PostgreSQL `published_at - occurred_at` in the local failure workload |

The transport guarantee is **at least once**, not exactly once. Exactly-once business side effects come from the consumer-side `processed_event(event_id)` primary key.

## Architecture

```text
POST /orders
  -> OrderService
  -> OrderTransaction port
  -> JdbcOutboxAdapter
       -> INSERT orders
       -> INSERT outbox_event     (same PostgreSQL transaction)

OutboxWorker x N
  -> OutboxStore.claimBatch
       -> SELECT ... FOR UPDATE SKIP LOCKED + lease
  -> EventPublisher port
       -> KafkaMessagePublisher -> Redpanda
  -> PUBLISHED or retryable FAILED

KafkaEventSource
  -> DeduplicatingEventConsumer
  -> processed_event(event_id)    (idempotency gate)
```

Dependency direction stays inward: domain records have no Spring, JDBC, Kafka, or transport imports; application use cases depend on small ports; infrastructure implements those ports. This applies SRP, ISP, DIP, LSP, KISS, and explicit failure semantics without sharing databases with other services.

## API

Start the local stack:

```powershell
docker compose up --build app
```

Create an order:

```http
POST /orders
Content-Type: application/json

{"sku":"book-01","quantity":1}
```

The public event envelope is versioned at `contracts/commerce-event-v1.schema.json` and contains `eventId`, `eventType`, `eventVersion`, `aggregateId`, `sagaId`, `correlationId`, `causationId`, `occurredAt`, and `payload`.

## Verification

```powershell
./gradlew.bat test
./tools/benchmark.ps1
./tools/validate-project.ps1 -SkipDocker
```

`./gradlew.bat test` includes PostgreSQL Testcontainers integration tests when a host JDK 21 is available. The Docker image runs unit and contract tests during the build; the benchmark is the real PostgreSQL + Redpanda end-to-end gate.

See [REFERENCES.md](REFERENCES.md) for primary sources and [sdd/](sdd/) for the decisions, benchmark plan, and continuation handoff.
