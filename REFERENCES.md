# References

Primary sources used for implementation decisions. No source code was copied.

| Area | Source | Applied decision |
|---|---|---|
| PostgreSQL locking | [PostgreSQL `SELECT`](https://www.postgresql.org/docs/current/sql-select.html) | `FOR UPDATE SKIP LOCKED` lets concurrent workers claim different rows without blocking |
| PostgreSQL transactions | [PostgreSQL transaction isolation](https://www.postgresql.org/docs/current/transaction-iso.html) | Order and outbox event are committed in one local ACID transaction |
| Kafka producer | [Apache Kafka producer configuration](https://kafka.apache.org/documentation/#producerconfigs) | `acks=all` and idempotent producer configuration for broker writes |
| Redpanda local runtime | [Redpanda single-broker Docker Compose lab](https://docs.redpanda.com/current/get-started/quick-start/) | Kafka-compatible local-first broker with a pinned Docker image |
| Flyway | [Flyway with Spring Boot](https://documentation.red-gate.com/flyway/reference/usage/community-plugins-and-integrations/community-plugins-and-integrations-spring-boot) | Versioned PostgreSQL schema migration at application startup |
| Spring JDBC transactions | [Spring transaction management](https://docs.spring.io/spring-framework/reference/data-access/transaction.html) | Explicit transaction boundary around both inserts |
| Testcontainers | [Testcontainers for Java PostgreSQL](https://java.testcontainers.org/modules/databases/postgres/) | Real PostgreSQL integration tests without a paid service |
| Repository governance | Local `.portfolio/` snapshot from `portfolio-reuse-kit` | SDD, decision records, V2 evidence contract, and reuse review |

## Reuse Boundary

The repository reuses governance contracts and patterns from `portfolio-reuse-kit`. Its SQL migration, commerce event schema, failure workload, application code, and benchmark measurements remain project-owned.
