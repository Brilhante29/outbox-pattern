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
- [ ] Fresh V2 result generated from a clean implementation commit.
- [ ] Validator passes against the fresh result.
- [ ] Final evidence commit created locally.
- [ ] Push performed only after explicit request.
