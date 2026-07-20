# Architecture Decision

## Status

Accepted

## Context

Project: outbox-pattern
Claim: outbox transacional — zero messages lost under failure
Benchmark: lost_messages_under_failure

Problem forces:

- Domain complexity: low
- Integration pressure: low
- UI state complexity: none
- Data/ML reproducibility: none
- Auditability/event history: low
- Throughput/async pressure: medium
- Independent deployability need: none

## Decision

Chosen architecture: hexagonal (ports/adapters)

Reason:

The transactional outbox pattern naturally maps to hexagonal architecture. The domain defines two ports (OutboxRepository and MessagePublisher). The application layer implements use cases (order creation + outbox event). Infrastructure provides in-memory adapters. The OutboxProcessor orchestrates recovery. This separation proves the claim without external infrastructure.

Dependency rule:

Domain/application do not depend on infra; adapters depend inward through ports.

## Rejected Alternatives

| Alternative | Why rejected |
|---|---|
| MVC | Couples domain to infrastructure; does not emphasize port/adapter separation |
| Event-driven microservices | Overkill for proving the outbox pattern claim |

## Folder Layout

```
src/main/java/com/portfolio/outbox/
  OutboxApplication.java
  domain/         (OutboxEvent, OutboxStatus, OutboxRepository, MessagePublisher, OutboxProcessor)
  application/    (OrderController, OrderService)
  infrastructure/ (InMemoryOutboxRepository, InMemoryMessagePublisher, SimulatedFailureInjector)
  benchmark/      (OutboxBenchmark, BenchmarkResult)
```

## Testing Strategy

- Unit tests: domain entities, repository, service
- Integration tests: benchmark end-to-end
- Benchmark: docker run with benchmark command

## Consequences

Positive:

- Clean separation of concerns
- Testability without infrastructure
- Ports can be swapped for real DB/broker later

Tradeoffs:

- In-memory store does not prove real DB transactional behavior

Migration path:

- Replace InMemoryOutboxRepository with Spring Data JPA + PostgreSQL
- Replace InMemoryMessagePublisher with Redpanda/Kafka producer
