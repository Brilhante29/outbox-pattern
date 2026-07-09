# #20 outbox-pattern

**Status:** scaffold

**Proves:** outbox transacional.

**Benchmark target:** lost_messages_under_failure.

**Stack:** java21, spring-boot, postgresql, redpanda, docker.

## Next milestone

Implement the smallest Docker-runnable version and produce the first JSON benchmark under enchmarks/results/.

## Run

`ash
docker build -t outbox-pattern .
docker run --rm outbox-pattern
`

## Benchmark

`ash
docker run --rm outbox-pattern benchmark
`

| Metric | Value | Unit |
|---|---:|---|
| lost_messages_under_failure | pending | pending |

## Architecture

Defined in sdd/spec.md before implementation.

## References

See REFERENCES.md.