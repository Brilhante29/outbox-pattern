# Technical Decision

## Status

Accepted

## Decision Type

stack

## Context

Project: outbox-pattern
Problem: Prove transactional outbox pattern prevents message loss under failure
Portfolio program: backend-reliability-platform
Public signal: Java/Spring Boot enterprise reliability pattern
Benchmark: lost_messages_under_failure

## Selected Option

Selected: Spring Boot 3.4 + Java 21 + in-memory adapters

Reason:

Spring Boot is the standard Java framework for transactional patterns. In-memory adapters avoid external infrastructure dependencies while proving the outbox recovery mechanism. The benchmark runs entirely in Docker with no paid services.

## Decision Brain Fields

- Stack profile: spring-kotlin-backend
- API style: rest-http
- Messaging: outbox-only
- Cloud mode: none
- Database/runtime: none (in-memory)
- Library policy: Minimal dependencies: spring-boot-starter-web, jackson-databind, jackson-datatype-jsr310

## Engineering Principles

Coupling boundary:

Domain/use cases must not depend on framework, DB, broker, cloud SDK, transport, or UI.

SOLID application:

- SRP: Each class has one responsibility (event, repository, publisher, processor, controller, service)
- OCP: New event types added without modifying existing events
- LSP: InMemoryOutboxRepository is substitutable for a real DB repository
- ISP: Small interfaces (save, markPublished, findPending)
- DIP: OutboxProcessor depends on OutboxRepository and MessagePublisher abstractions

Simplicity:

- KISS: In-memory stores simulate DB and broker; no external infrastructure needed
- YAGNI: No JPA, no Hibernate, no real database — not needed to prove the claim
- DRY: No duplicated business knowledge; event creation encapsulated in OrderService

Testability evidence:

- OrderServiceTest tests use case without transport/infrastructure
- InMemoryOutboxRepositoryTest tests adapter behavior

## Rejected Options

| Option | Why rejected |
|---|---|
| Spring Data JPA + PostgreSQL | Adds latency and complexity; in-memory suffices for benchmark |
| Real Redpanda/Kafka | Would require running containers and managing topics |
| Kotlin instead of Java | Java is more canonical for enterprise outbox pattern examples |

## API Contract

REST HTTP:

- POST /orders -> 200 {"orderId": "uuid"}

## Cloud Local-First

Local provider: none (Docker only)
Real provider target: none
Config switch: none

## Benchmark Impact

Expected impact: lost_messages_under_failure = 0 (outbox pattern guarantees recovery)

Validation command:

```bash
docker run --rm outbox-pattern benchmark
```

## Operational Cost

- Docker services added: none (single container)
- Local demo complexity: low
- Failure case required: yes (SimulatedFailureInjector)

## Follow-up

If benchmark shows lost messages > 0, investigate OutboxProcessor error handling and retry logic.
