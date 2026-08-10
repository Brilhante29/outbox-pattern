# Benchmark Plan: Transactional Outbox Failure Recovery

## Hypothesis

After a producer JVM dies after committing an order and before publishing, every outbox event remains recoverable. Broker outage creates retries, and acknowledgement loss creates duplicates, but unique consumer side effects still equal committed events.

## Command

```powershell
./tools/benchmark.ps1
```

The harness requires a clean Git tree, removes the prior artifact, builds a tagged image, captures its image digest, starts PostgreSQL and Redpanda, and runs the benchmark with a host result mount.

## Workload

- Repetitions: 3.
- Producer failure: child JVM calls `Runtime.halt(137)` immediately after `OrderService.createOrder` returns.
- Broker failure: first worker uses unreachable bootstrap server `127.0.0.1:1`.
- Duplicate failure: publisher sends successfully, then loses the acknowledgement before the outbox status update once per event.
- Recovery concurrency: 3 workers, one event per claim.
- Consumer: reads 6 raw records and persists 3 unique `eventId` values.
- Warm-up: 0; this is a correctness/failure benchmark, not a throughput benchmark.

## Metrics

| Metric | Unit | Acceptance |
|---|---|---:|
| `lost_messages` | messages | 0 |
| `duplicates` | messages | observed and deduplicated |
| `publish_lag_p95` | milliseconds | reported, lower is better |
| `retry_count` | retries | reported with failure breakdown |

## Evidence Contract

- Schema: `.portfolio/contracts/benchmark-result-v2.schema.json`.
- Output: `benchmarks/results/outbox-benchmark-v2.json`.
- Producer validates the full JSON against the V2 schema before writing.
- Provenance includes commit, clean-tree assertion, image ID, dependency lock digest, producer, and measurement artifact digest.
- The artifact is never accepted merely because an older tracked JSON exists.

## Limits

This three-event workload measures correctness and recovery mechanics, not production throughput. `publish_lag_p95` is machine-dependent and should only be compared with the same `comparability_key`.
