# #20 outbox-pattern

**Status:** benchmarked

**Proves:** outbox transacional — zero messages lost under simulated system failure.

**Benchmark target:** lost_messages_under_failure.

**Result:** 0 messages lost (out of 100 events, 30% failure rate after write but before publish).

**Stack:** java21, spring-boot, postgresql, redpanda, docker.

## Run

```bash
docker build -t outbox-pattern .
docker run --rm outbox-pattern benchmark
```

## Benchmark

```bash
docker run --rm outbox-pattern benchmark
```

| Metric | Value | Unit |
|---|---|---:|---|
| lost_messages_under_failure | 0 | messages |

## Architecture

Hexagonal (ports/adapters). Domain defines `OutboxRepository` and `MessagePublisher` ports. Infrastructure provides in-memory adapters. `OutboxProcessor` polls pending events and publishes them — simulating recovery after failure.

## References

See REFERENCES.md.
