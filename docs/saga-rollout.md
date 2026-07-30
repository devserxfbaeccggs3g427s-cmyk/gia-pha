# Saga rollout

## Sessions

- Base session: delete-member and delete-tree owner-side Saga persistence, barriers, reply processing, and compensation staging.
- Session I: bounded retry using real step attempt counts, manual-review escalation, irreversible-boundary compensation guard, malformed-reply DLQ persistence, and Audit Ops failure routing.
- Session J: deadline scanner, retry scheduler, and fault-injection verification for outage, duplicate, reorder, broker restart, and orchestrator restart scenarios.

## ADR references

- ADR-003: Saga state machines and eventual consistency.
- ADR-004: Kafka delivery, retry, replay, and DLQ governance.

## Acceptance criteria

- A failed participant step is re-dispatched only while `attemptCount < maxAttempts`.
- An exhausted step transitions the owning Saga to `MANUAL_REVIEW` and publishes `OperationStateChanged`.
- Compensation is staged only for acknowledged, compensatable steps before the irreversible boundary.
- Malformed replies are persisted in `saga_dead_letter` with topic, partition, offset, payload, error, and timestamp.
- Audit Ops projects `failureRouting` as `COMPENSATING`, `MANUAL_REVIEW`, or `DLQ`.
- Session J remains responsible for deadline scanning and fault-injection tests.
