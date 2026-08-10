# Reuse Improvement Review

Project: `#20 outbox-pattern`

## Review Points

- [x] after replacing simulated infrastructure
- [x] after architecture and stack decision
- [x] after the first real PostgreSQL slice
- [x] after the failure benchmark harness
- [x] before publication

## Findings

| Finding | Decision | Kit area | Reason | Follow-up |
|---|---|---|---|---|
| A tracked benchmark can stay green while the producer no longer writes it | `backlog` | benchmark harness | Kit validators should require freshness/provenance, not file presence | Add stale-artifact gate template |
| Correctness benchmarks need real failure boundaries | `backlog` | failure harness | In-memory failure flags cannot prove durability | Add hard-child-process crash pattern |
| Outbox projects need at-least-once and consumer-idempotency language | `backlog` | messaging decisions | Prevents false exactly-once claims | Add outbox decision checklist |
| V2 artifacts benefit from producer-side schema validation | `backlog` | evidence contracts | CI should fail before writing invalid evidence | Add JVM validator example |
| Project SQL and event payload are domain-specific | `reject` | templates | Moving them into the kit would couple unrelated repositories | Keep project-owned |

## Resolution

The task is restricted to this repository, so reusable improvements are recorded for the principal agent to apply to `portfolio-reuse-kit` after this repository passes. Project-specific SQL, Java classes, and benchmark values remain here.

## Final Gate

- [x] Reusable improvements were patched or recorded.
- [x] Project-specific implementation was not moved into the kit.
- [x] Validation reflects the stale-artifact and real-infrastructure mistakes discovered here.
