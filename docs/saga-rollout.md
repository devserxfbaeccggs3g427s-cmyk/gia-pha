# Saga rollout

## Sessions

- Base session: delete-member and delete-tree owner-side Saga persistence, barriers, reply processing, and compensation staging.
- Session I: bounded retry using real step attempt counts, manual-review escalation, irreversible-boundary compensation guard, malformed-reply DLQ persistence, and Audit Ops failure routing.
- Session J: deadline scanner, retry scheduler, and fault-injection verification for outage, duplicate, reorder, broker restart, and orchestrator restart scenarios.

## Session J — deadline scanner & retry scheduler

Sub-tasks delivered in this session (no tests authored; fault-injection and automated tests remain deferred until Task 13.6):

- 13.4.J.1 — Flyway V5 migration adds `next_attempt_at`, `step_deadline_at`, `dispatch_token`, `last_failure_at`, plus `(state, next_attempt_at)` and `(state, step_deadline_at)` indexes to `delete_member_saga_step` and `delete_tree_saga_step`.
- 13.4.J.2 — `DeleteMemberSagaStep` / `DeleteTreeSagaStep` expose `nextAttemptAt`, `stepDeadlineAt`, `dispatchToken`, `lastFailureAt` with idempotent transitions `claimDispatch`, `acknowledge`, `scheduleRetry`, `markCompensated`, `markFailed`, `retryDue`, `timedOut`, `exhausted`, `isTerminal`. `attemptCount` increases only on a successful claim; terminal steps skip retry.
- 13.4.J.3 — Owner repositories add `listRetryableSteps`, `listTimedOutSteps`, `tryClaimDispatch`, `releaseOrScheduleRetry`, `findActiveStep`, `markOperationManualReview`. The claim uses an atomic UPDATE with `dispatch_token IS NULL` and a state guard, so concurrent replicas cannot both dispatch the same step.
- 13.4.J.4 — `SagaRetryPolicy` computes `min(maxBackoff, baseBackoff × 2^(attempt-1))` with bounded jitter, guards overflow, and clamps `nextAttemptAt` to the operation deadline. `SagaClock` and `SagaJitterSource` are injectable.
- 13.4.J.5 / 13.4.J.6 — `DeleteMemberSagaDeadlineScanner` and `DeleteTreeSagaDeadlineScanner` run under `@Scheduled(fixedDelayString=...)` with `@ConditionalOnProperty` `familya.{member,treeauth}.saga.scheduler-enabled` (default `true`). They batch-scan timed-out steps, retryable steps, and expired operations, fail-isolated per operation, and never block on Kafka publish.
- 13.4.J.7 — Reply processors now refuse to advance forward state while the Saga is in `COMPENSATING`; compensation replies update their own step and only use `markCompensated` / compensation-failure paths.
- 13.4.J.8 — Failure codes `STEP_TIMEOUT`, `OPERATION_DEADLINE_EXCEEDED`, `RETRY_EXHAUSTED`, `COMPENSATION_RETRY_EXHAUSTED`, `DISPATCH_CLAIM_CONFLICT` are surfaced via `OperationStateChanged.failureRouting` and the owner-side DLQ tables. No PII or raw payload fields are emitted.
- 13.4.J.9 — `@ConfigurationProperties` (`familya.{member,treeauth}.saga.{deadline,retry,scheduler-enabled}`) bind the deadline scanner, retry, and enablement knobs. `SagaConfig` exposes `SagaClock`, `SagaJitterSource`, and `SagaRetryPolicy` beans.
- 13.4.J.10 — `@EnableScheduling` is already present in both service applications; the scanner bean carries `@ConditionalOnProperty` to disable the scheduler in migration or test contexts, and each batch iteration is wrapped in a short `@Transactional` unit.

Acceptance status:
- Deadline scanner handles step timeout and retry due — implemented.
- Operation deadline is distinct from step deadline — implemented (`deadline_at` vs `step_deadline_at`).
- `attemptCount` increments only at dispatch — enforced via `claimDispatch`/atomic UPDATE.
- Retry claim is safe across replicas — enforced via `dispatch_token IS NULL` guard.
- State and outbox commit atomically — preserved by reusing `OutboxDelete*SagaGateway.stageOperationStateChanged`.
- Retry never exceeds `maxAttempts` / operation deadline — `SagaRetryPolicy` clamps and `releaseOrScheduleRetry` is only called when `attemptCount < maxAttempts`.
- Compensation retry keeps `RESTORE_*` step codes — owned by `OutboxDelete*SagaGateway.compensationStepCode`.
- Irreversible boundary routes to `MANUAL_REVIEW` — `passedIrreversibleBoundary` already enforced; scanner reuses the same flag.
- Audit Ops remains a passive projection — only `failureRouting` is added to the lifecycle payload.
- Fault-injection / automated tests — deferred until Task 13.6.
- Task 13.6 — not delivered.
- Task 13 parent — not marked complete while 13.5/13.6 remain open.

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
