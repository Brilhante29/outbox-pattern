# Spec: outbox-pattern

## Number

#20

## Claim

Outbox transacional — the transactional outbox pattern prevents message loss when the system fails after writing to the database but before publishing to the message broker.

## Stack

java21, spring-boot, postgresql, redpanda, docker

## User-visible output

- Docker command: `docker run --rm outbox-pattern benchmark`
- README opens with: # #20 outbox-pattern
- Benchmark table: lost_messages_under_failure = 0

## Scope

In:

- Implementar o menor produto funcional que prove o claim.
- Rodar por Docker.
- Gerar benchmark JSON reproduzivel.

Out:

- Publicar repo antes do primeiro resultado numerico.
- Depender de segredo pago para o caminho default.

## Architecture

Hexagonal (ports/adapters). Domain defines OutboxRepository and MessagePublisher ports. Infrastructure implements in-memory adapters. OutboxProcessor polls pending events and publishes them.

## Benchmark

Primary metric:

- name: lost_messages_under_failure
- target: 0 (zero lost messages under failure)
- command: `docker run --rm outbox-pattern benchmark`
- result file: benchmarks/results/lost_messages_under_failure.json

## Dataset or fixture

- source: synthetic (generated in benchmark)
- size: 100 events per run
- license: project-specific
- deterministic seed: 42

## Definition of done

- [x] Docker command works from clean clone.
- [x] README starts with project number and benchmark result.
- [x] Benchmark command writes JSON result.
- [x] Tests cover core behavior.
- [x] REFERENCES.md explains reuse.
- [x] No secret or paid credential required for default demo.
