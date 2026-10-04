# Transactional Outbox: Zero Lost Events Across Forced Crashes and Broker Outages

**0 lost messages** across 3 forced JVM crashes, with **3 duplicate deliveries safely deduplicated** and **6 retries**, using a PostgreSQL transactional outbox, competing workers, and a Kafka-compatible broker (Redpanda).

[![CI](https://github.com/Brilhante29/outbox-pattern/actions/workflows/ci.yml/badge.svg)](https://github.com/Brilhante29/outbox-pattern/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
![Java 21](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-6DB33F?logo=springboot&logoColor=white) ![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-4169E1?logo=postgresql&logoColor=white)

## Why this exists

"Save the order, then publish the event" is two writes to two systems, and one of them will eventually fail in between. If the process dies after the database commit, the event is lost; if it publishes first and the commit fails, the event describes something that never happened. Distributed transactions are rarely available or worth it. The transactional outbox solves this, but only if the hard parts are actually tested: crashes at the worst moment, broker outages, and duplicates. This repository tests exactly those:

- the order and its event are written in **one PostgreSQL transaction**;
- workers claim batches with `SELECT ... FOR UPDATE SKIP LOCKED` plus a lease, so several can run concurrently without double-claiming;
- the benchmark hard-kills three producer JVMs (exit code `137`) right after each commit, makes the broker unavailable, and loses acknowledgements on purpose;
- the consumer deduplicates by `processed_event(event_id)`, turning at-least-once delivery into exactly-once business effects.

## Results

| Metric | Result | Meaning |
|---|---:|---|
| `lost_messages` | **0** | Every committed outbox event reached the consumer |
| `duplicates` | **3** | Deliberate post-publish acknowledgement losses produced one duplicate per event |
| `retry_count` | **6** | One broker-outage retry and one acknowledgement-loss retry per event |
| `publish_lag_p95` | **14,308.564 ms** | PostgreSQL `published_at - occurred_at` in the failure workload |

The transport guarantee is **at least once**, not exactly once. The publish lag is dominated by the injected outage and retry backoff, so it describes recovery time under failure, not steady-state latency.

## Quickstart

Requirements: Docker (with Compose) and PowerShell 7 for the benchmark.

```powershell
./tools/benchmark.ps1
```

The command starts PostgreSQL and Redpanda, builds the application, runs the crash, outage, and duplicate scenarios with three concurrent workers, and writes a fresh artifact to `benchmarks/results/outbox-benchmark-v2.json`. It refuses a dirty Git tree so the evidence provenance stays truthful.

Run the service:

```bash
docker compose up --build app
curl -s -X POST localhost:8080/orders -H 'Content-Type: application/json' -d '{"sku":"book-01","quantity":1}'
```

The public event envelope is versioned in [`contracts/commerce-event-v1.schema.json`](contracts/commerce-event-v1.schema.json) and carries `eventId`, `eventType`, `eventVersion`, `aggregateId`, `sagaId`, `correlationId`, `causationId`, `occurredAt`, and `payload`.

## How it works

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

Dependency direction stays inward: domain records have no Spring, JDBC, Kafka, or transport imports; use cases depend on small ports; infrastructure implements them. No database is shared with other services.

## Design decisions

| Decision | Why | Rejected |
|---|---|---|
| Transactional outbox in the service database | Atomic state change and event intent with no distributed transaction | Dual writes; two-phase commit |
| `FOR UPDATE SKIP LOCKED` with leases | Horizontal workers without a coordinator, and recovery from dead workers | A single publisher process |
| Consumer-side dedup table | Exactly-once effects on top of at-least-once transport | Trusting broker exactly-once across systems |
| Polling worker | Simple, observable, testable under crashes | CDC (Debezium) before polling becomes the bottleneck |
| Redpanda locally | Kafka API in one container | A multi-broker cluster for a local proof |

## Testing

```powershell
./gradlew test
./tools/validate-project.ps1 -SkipDocker
```

`test` includes PostgreSQL Testcontainers integration tests when a host JDK 21 is available. The Docker image runs unit and contract tests during the build; the benchmark is the real PostgreSQL plus Redpanda end-to-end gate.

## Limitations

- Three events in the failure workload: the benchmark proves semantics under failure, not throughput.
- Polling adds latency compared with log-based CDC.
- Single PostgreSQL instance; replica failover is out of scope.

## Project structure

```text
src/main/java/com/portfolio/outbox/   domain, ports, JDBC outbox adapter, workers, Kafka publisher and consumer
src/main/resources/db/migration/     Flyway schema (orders, outbox_event, processed_event)
src/test/   unit, contract, and Testcontainers integration tests
contracts/  versioned commerce event envelope
benchmarks/ failure-workload results (V2)
tools/      benchmark harness and validators
sdd/  openspec/  decisions, benchmark plan, continuation handoff
```

## How this repository is built

The project follows the spec-driven workflow of [portfolio-reuse-kit](https://github.com/Brilhante29/portfolio-reuse-kit). Requirements and decisions live in [`sdd/`](sdd) and [`openspec/`](openspec), and [`project.yaml`](project.yaml) records the architecture, stack, and rejected alternatives. Development is AI-assisted and human-governed: [`AGENTS.md`](AGENTS.md) and [`CLAUDE.md`](CLAUDE.md) hold the coding-agent instructions, while tests, validators, and CI decide what gets published.

## Related work

- [saga-orchestrator](https://github.com/Brilhante29/saga-orchestrator): compensation across services that consume these events.
- [event-sourcing-orders](https://github.com/Brilhante29/event-sourcing-orders): events as the source of truth instead of an outbox.
- [spring-hexagonal-payments](https://github.com/Brilhante29/spring-hexagonal-payments): database-enforced idempotency on the command side.

See [`REFERENCES.md`](REFERENCES.md) for primary sources.

## Author

**Guilherme Brilhante**, software engineer working on scalable backends and production AI.
[LinkedIn](https://www.linkedin.com/in/guilhermefreirebrilhanteseveriano/) · [GitHub](https://github.com/Brilhante29) · [Publications](https://dblp.org/pid/353/6812.html)

## License

[MIT](LICENSE).
