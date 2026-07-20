# Agent Handoff

Project: `20 - outbox-pattern`

## Principal Agent Summary

- Objective: Implement transactional outbox pattern and benchmark lost_messages_under_failure
- Portfolio program: backend-reliability-platform
- Public proof claim: outbox transacional — zero messages lost under failure
- Primary benchmark: lost_messages_under_failure
- Default runnable path: docker run --rm outbox-pattern benchmark

## Subagent Decisions

| Role | Decision | Evidence Path | Status |
|---|---|---|---|
| `program-planner` | backend-reliability-platform | `project.yaml`, `sdd/spec.md` | done |
| `architecture-selector` | hexagonal (ports/adapters) | `sdd/architecture-decision.md` | done |
| `engineering-principles-reviewer` | SOLID applied, DIP maintained | `project.yaml`, `sdd/technical-decision.md` | done |
| `stack-decision-agent` | Spring Boot 3.4 + Java 21 | `project.yaml`, `sdd/technical-decision.md` | done |
| `api-style-agent` | REST HTTP (POST /orders) | OrderController.java | done |
| `cloud-local-first-agent` | None (Docker only) | Dockerfile | done |
| `messaging-agent` | Outbox-only (in-memory publisher) | `sdd/technical-decision.md` | done |
| `language-profile-agent` | Java with Gradle | repo layout, tests, tooling | done |
| `benchmark-harness-agent` | OutboxBenchmark + BenchmarkResult | `sdd/benchmark-plan.md`, `benchmarks/results/` | done |
| `design-system-agent` | README with benchmark table | `README.md` | done |
| `security-reuse-reviewer` | No secrets, REFERENCES.md complete | `REFERENCES.md`, release checklist | done |
| `release-ci-publisher` | CI workflows, validation pass | validation and CI | done |

## Local-First Runtime

- Docker command: `docker run --rm outbox-pattern benchmark`
- Local services: none (single container)
- Kumo services: none
- Real cloud adapter target: none
- Config switch: none
- Default path requires paid secret: no

## Architecture Boundaries

- Domain boundaries: OutboxEvent, OutboxStatus, OutboxRepository, MessagePublisher, OutboxProcessor
- Use-case boundaries: OrderService, OrderController
- Ports: OutboxRepository, MessagePublisher
- Adapters: InMemoryOutboxRepository, InMemoryMessagePublisher
- Dependency direction rule: Domain and application do not depend on infrastructure

## Benchmark Handoff

- Metric: lost_messages_under_failure
- Unit: messages
- Higher or lower is better: lower (0 is ideal)
- Command: `docker run --rm outbox-pattern benchmark`
- Result path: benchmarks/results/lost_messages_under_failure.json
- Dataset or fixture: synthetic (100 events, seed=42)

## Open Risks

- None

## Publication Gates

- [x] Docker path works
- [x] benchmark result exists
- [x] README starts with number, claim, and benchmark
- [x] references are documented
- [x] no secret in files or git remote
- [x] validation passes
