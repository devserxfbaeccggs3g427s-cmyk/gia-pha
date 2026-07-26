# ADR-102: Saga style

> Status: **Accepted** (per design.md §Saga style).
> Phase: 0.1 (initial direction); concrete sagas implemented in Phase 6.1.

## Decision

- **Choreography** for fan-out (e.g. `MemberCreated` triggers
  `audit`, `sharing`, `reporting`, `relationships-graph` consumers).
- **Orchestration** for non-trivial workflows with compensations:
  `ImportSaga`, `ExportSaga`, `DeleteMemberSaga`, `DeleteTreeSaga`,
  `RestoreSnapshotSaga`.

## Rationale

- Fan-out is the natural shape of a single event → many consumers.
  Choreography keeps the producer ignorant of consumers, which is the
  right coupling.
- Multi-step workflows with compensations (e.g. `ImportSaga`) need
  explicit state and a saga log. A central orchestrator makes that
  observable and replayable.

## Consequences

- The orchestrator runs in `transfer-service` for import/export/restore
  and in `tree-service` for tree-level cascades (per design.md §Sagas).
- Saga state lives in a `saga_log` table inside the orchestrator's schema.
- Replay tooling is built in Phase 6.1.
