# Spec: #20 outbox-pattern

## Measurable Claim

A real PostgreSQL transactional outbox delivers every committed commerce event to a Kafka-compatible consumer after process and broker failures: `lost_messages = 0`.

## Problem

Writing an order and publishing its event are two independent operations. A process can die between them. Direct dual writes therefore allow committed orders with missing events. The solution must preserve the event durably, publish it at least once, and make duplicate consumer deliveries harmless.

## In Scope

- Atomic `orders` and `outbox_event` inserts in PostgreSQL.
- Concurrent claims using `FOR UPDATE SKIP LOCKED` plus expiring leases.
- Retry of `PENDING`, `FAILED`, and expired `PROCESSING` rows.
- Kafka-compatible publication to local Redpanda.
- Consumer-side deduplication by `eventId` in PostgreSQL.
- Versioned commerce event JSON Schema.
- Three hard JVM crashes after commit and before publish.
- Broker outage and post-publish acknowledgement-loss scenarios.
- V2 benchmark artifact regenerated on a host bind mount.

## Out of Scope

- Exactly-once Kafka transport claims.
- Distributed transactions or two-phase commit.
- Production broker clustering, schema registry, or Kubernetes.
- Business workflow orchestration; that belongs to `saga-orchestrator`.

## Default Path

- Command: `./tools/benchmark.ps1`
- Local services: PostgreSQL 17.6 and Redpanda 26.1.14 via Docker Compose.
- Paid credentials: none.
- Output: `benchmarks/results/outbox-benchmark-v2.json`.

## Acceptance

- `lost_messages = 0`.
- Three measured crash repetitions.
- One duplicate delivery per event is observed and deduplicated.
- All outbox rows finish `PUBLISHED`.
- Processed side effects equal unique event IDs.
- Result passes the local V2 JSON Schema validator before it is written.
