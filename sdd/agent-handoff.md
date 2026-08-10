# Agent Handoff

Project: `#20 outbox-pattern`

## Current State

- Branch: `codex/backend-reliability-close`.
- Architecture: hexagonal Java/Spring JDBC.
- Runtime: PostgreSQL 17.6 plus Redpanda 26.1.14 in Docker Compose.
- Contract: `contracts/commerce-event-v1.schema.json`.
- Benchmark: `tools/benchmark.ps1` -> `benchmarks/results/outbox-benchmark-v2.json`.
- Evidence source: clean implementation commit `52d6b7018fb335aaed3fbd1cb094c1e1e636d236`.
- Result: `lost_messages=0`, `duplicates=3`, `retry_count=6`, `publish_lag_p95=14308.564 ms`.
- Push policy for this change: do not push.

## Implemented Decisions

| Concern | Decision | Evidence |
|---|---|---|
| Atomicity | Order and outbox insert share one `TransactionTemplate` | `JdbcOutboxAdapter.persist` and PostgreSQL integration test |
| Concurrent workers | CTE plus `FOR UPDATE SKIP LOCKED` and expiring lease | `JdbcOutboxAdapter.claimBatch` |
| Retry | `PENDING`, `FAILED`, and expired `PROCESSING` are claimable | migration, adapter, benchmark |
| Broker | Kafka client with Redpanda local-first | `KafkaMessagePublisher`, `compose.yaml` |
| Deduplication | `processed_event(event_id)` primary key | `DeduplicatingEventConsumer`, adapter |
| Failure proof | Hard JVM halt, unavailable broker, lost acknowledgement | `BenchmarkCommand` |
| Evidence | V2 result validated before host write | `JsonSchemaContract`, benchmark command |

## Continuation Commands

```powershell
git status --short --branch
./tools/benchmark.ps1
./tools/validate-project.ps1 -SkipDocker
```

Use `./gradlew.bat test` when JDK 21 exists on the host. Without a host JDK, `docker build -t outbox-pattern:local .` runs unit and contract tests, while the Compose benchmark exercises PostgreSQL and Redpanda end to end.

## Remaining Publication Sequence

1. Inspect the final local evidence commit and exact diff.
2. Push only when explicitly requested by the principal/user.

This file records observable decisions and commands only; it does not contain private reasoning or chain-of-thought.
