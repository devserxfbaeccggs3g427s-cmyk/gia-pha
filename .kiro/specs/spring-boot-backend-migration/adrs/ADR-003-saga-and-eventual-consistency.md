# ADR-003: Orchestrated Saga and Eventual Consistency

- **Status:** Accepted
- **Date:** 2026-07-27

## Context

Delete-member, delete-tree, media activation, cutover, import, and restore cross service boundaries. A distributed transaction is unavailable and would couple service availability and ownership.

## Decision

The service owning a use case runs an orchestrated Saga. Every participant acknowledges only after a local transaction commits state and outbox. Standard operation states are `PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`, `COMPENSATING`, `COMPENSATED`, and `MANUAL_REVIEW`. Commands and compensations are idempotent; timeouts, irreversible boundaries, and operator actions are explicit.

Reads hide tombstoned, pending-deletion, and inactive-epoch data. Success requires mandatory participants to reach the target revision/epoch. Audit & Operations projects Saga state for observation but does not own business orchestration.

## Consequences

Immediate global consistency and cross-domain rollback are not promised. Users receive durable operation status. Compensation can restore visibility only before irreversible cleanup; some failures require manual review.

## Verification

Fault injection must cover every transition, duplicate/reordered delivery, participant and broker outage, orchestrator restart, compensation replay, and irreversible boundaries. Tests must prove no invisible stuck operation or false success.
