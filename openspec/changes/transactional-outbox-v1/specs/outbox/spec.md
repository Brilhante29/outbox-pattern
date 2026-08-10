# Outbox Reliability Requirements

## Atomic Write

The system MUST insert the order and its outbox event in one PostgreSQL transaction. If either insert fails, neither row may remain.

## Concurrent Claim

Workers MUST claim disjoint rows with database locking and a lease. `FAILED` and expired `PROCESSING` rows MUST remain retryable.

## Delivery

The publisher MUST send the versioned commerce event envelope through the Kafka protocol. The local-first broker MUST be Redpanda.

## Idempotency

The consumer MUST use `eventId` as a persistent uniqueness key before applying a business side effect.

## Evidence

The benchmark MUST hard-stop three producer JVMs after commit, recover after broker failure, observe duplicate delivery, report V2 metrics, and regenerate the artifact on the host.
