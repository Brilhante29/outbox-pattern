# Agent Handoff

Project: `#20 outbox-pattern`

## Current State

- Branch: `codex/backend-reliability-close`.
- Architecture: hexagonal Java/Spring JDBC.
- Runtime: PostgreSQL 17.6 plus Redpanda 26.1.14 in Docker Compose.
- Contract: `contracts/commerce-event-v1.schema.json`.
- Benchmark: `tools/benchmark.ps1` -> `benchmarks/results/outbox-benchmark-v2.json`.
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

1. Commit code and docs without a generated result.
2. Run `tools/benchmark.ps1` from that clean commit.
3. Update README only if the deterministic headline metrics differ.
4. Commit the regenerated V2 artifact and final handoff/status.
5. Push only when explicitly requested by the principal/user.

This file records observable decisions and commands only; it does not contain private reasoning or chain-of-thought.
