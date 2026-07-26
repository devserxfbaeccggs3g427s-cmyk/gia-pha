# ADR-103: Broker

> Status: **Accepted** (per design.md §Broker).
> Phase: 0.1 (initial direction); wired in Phase 0.3.

## Decision

RabbitMQ in Docker Compose, with the **outbox-as-a-service** pattern:
each service writes outbox rows in its local transaction; a relay
(`service-common.OutboxRelay`) reads them and publishes to RabbitMQ.

## Rationale

- RabbitMQ is well-understood, has a Spring Boot starter, and supports
  publisher-confirms / dead-letter semantics.
- The outbox pattern decouples broker availability from local commits —
  the retry-storm and broker-down failure modes (design.md §Failure
  Modes) are handled by the relay.

## Consequences

- Per-service outbox tables live in each service's schema.
- The relay is one scheduled job per service in this research lane.
- No Kafka. Recording the decision so Phase 7's comparison can address it.
