# Release Checklist

- [x] Docker image build fails on test failures.
- [x] Docker Compose provides PostgreSQL and Redpanda without secrets.
- [x] Order and outbox inserts are one real PostgreSQL transaction.
- [x] Concurrent workers use locking plus leases.
- [x] Failed and expired work is retryable.
- [x] Kafka event contract is versioned and producer-validated.
- [x] Consumer side effects are deduplicated by `eventId`.
- [x] Benchmark removes and regenerates the host artifact.
- [x] Benchmark uses three process crashes and a broker outage.
- [x] Fresh V2 result generated from clean implementation commit `52d6b7018fb335aaed3fbd1cb094c1e1e636d236`.
- [x] Validator passes against the fresh result.
- [x] Final evidence commit created locally.
- [ ] Push performed only after explicit request.
