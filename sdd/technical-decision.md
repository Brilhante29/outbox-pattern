# Technical Decision

## Selected Stack

- Java 21 and Gradle Kotlin DSL.
- Spring Boot 3.4 with Spring MVC and Spring JDBC.
- PostgreSQL 17.6 with Flyway migrations.
- Apache Kafka client against Redpanda 26.1.14.
- Jackson plus NetworkNT JSON Schema validation.
- JUnit 5 and Testcontainers PostgreSQL.
- Docker Compose and PowerShell harness.

## Why Java, Not Kotlin Here

The backend reliability program already has Kotlin in `spring-hexagonal-payments`. Keeping this repository in Java demonstrates JVM interoperability and enterprise Java proficiency while sharing the same event envelope and operational conventions. Kotlin conversion would not improve the outbox guarantee.

## MVC vs WebFlux

Spring MVC is selected because PostgreSQL JDBC and the transaction boundary are blocking. WebFlux would not make the path non-blocking and would obscure the consistency proof.

## Persistence

Spring JDBC is selected over JPA. The two essential operations are explicit:

1. One transaction inserts `orders` and `outbox_event`.
2. One statement claims rows with `FOR UPDATE SKIP LOCKED` and writes a lease.

Flyway owns all schema changes. The `(event_id)` primary keys enforce producer identity and consumer idempotency.

## Messaging

The application targets the Kafka protocol through `EventPublisher`. Redpanda is the no-secret local-first implementation. A managed Kafka-compatible service can replace it by changing `KAFKA_BOOTSTRAP_SERVERS`; domain and application code do not change.

Producer configuration uses `acks=all` and Kafka idempotence. That reduces broker duplicates but does not remove duplicates caused by a crash after broker acknowledgement and before the database status update. The consumer therefore records `eventId` before applying a side effect.

## Retry and Lease

- Claimable states: `PENDING`, `FAILED`, and expired `PROCESSING`.
- Claim order: lowest attempt count, then oldest event.
- Claim isolation: `FOR UPDATE SKIP LOCKED`.
- Lease: owner plus expiration timestamp.
- Publish failure: `FAILED`, error recorded, next attempt timestamp set.
- Publish success: `PUBLISHED`, publication timestamp recorded.

## Cloud Boundary

No cloud dependency is needed for the default path. Kumo is not used because this repo needs Kafka and PostgreSQL semantics, not AWS API emulation. Real cloud targets stay behind JDBC and Kafka configuration/adapters.
