# Transactional Outbox V1

## Intent

Replace the in-memory outbox simulation with a real PostgreSQL transaction, Kafka-compatible delivery, consumer idempotency, and a reproducible failure benchmark.

## Portfolio Impact

This repository becomes the delivery guarantee between payments/orders, saga orchestration, and downstream read models in the Backend Reliability Platform.

## Public Proof

- Primary metric: `lost_messages = 0`.
- Failure boundaries: hard JVM halt, broker unavailable, acknowledgement lost after publish.
- Artifact: `benchmarks/results/outbox-benchmark-v2.json`.
