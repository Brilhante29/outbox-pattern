# Architecture Decision: Hexagonal Transactional Outbox

## Status

Accepted.

## Forces

- Consistency and failure recovery are the claim, so real database and broker semantics are mandatory.
- Claim-critical SQL must remain visible and testable.
- Kafka and PostgreSQL must be replaceable without changing domain records or application policy.
- At-least-once delivery makes duplicates normal, not exceptional.

## Decision

Use a small hexagonal Spring Boot service:

```text
HTTP adapter -> OrderService -> OrderTransaction port -> JdbcOutboxAdapter
OutboxWorker -> OutboxStore port -> JdbcOutboxAdapter
OutboxWorker -> EventPublisher port -> KafkaMessagePublisher
DeduplicatingEventConsumer -> ProcessedEventStore port -> JdbcOutboxAdapter
```

`JdbcOutboxAdapter.persist` owns one explicit transaction containing the order insert and the outbox insert. Worker claims are one PostgreSQL statement combining `FOR UPDATE SKIP LOCKED` with a lease update. Kafka publication occurs outside the database transaction; success updates the row to `PUBLISHED`, while any failure returns it to retryable `FAILED`.

## Dependency Rule

- Domain imports only JDK types.
- Application imports domain and application ports.
- HTTP, JDBC, Kafka, JSON Schema, Docker, and Spring remain in adapters/configuration.
- Benchmark code may inspect adapter metrics but does not move policy into the domain.

## Principles Evidence

- SRP: create order, claim work, publish, and deduplicate are separate responsibilities.
- OCP: alternative databases or brokers implement the same small ports.
- LSP: fakes used by unit tests and real adapters preserve the port contracts.
- ISP: transaction, outbox, publisher, and processed-event ports are separate.
- DIP: use cases depend on ports, not JDBC or Kafka clients.
- KISS: explicit JDBC SQL replaces an ORM because locking SQL is the proof.
- YAGNI: no distributed transaction, broker abstraction framework, or Kubernetes.

## Rejected Alternatives

| Alternative | Rejection |
|---|---|
| In-memory maps | Cannot prove durability, transaction atomicity, locking, or broker recovery |
| Direct DB + Kafka dual write | Leaves a crash window with a committed order and missing event |
| Two-phase commit | Kafka and PostgreSQL coupling is heavier than at-least-once plus idempotency |
| JPA/Hibernate | Hides claim-critical locking and update semantics without adding value here |
| Kafka exactly-once claim | Does not make an external database side effect exactly once by itself |
