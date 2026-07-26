# ADR-008: Outbox, upload intent and cleanup instead of distributed transaction

Status: Accepted · 2026-07 · Requirements: 7, 13

## Context
Media upload spans MySQL (metadata) and Blob (bytes) — two systems with no shared
transaction. Legacy handles this with compensation (delete blobs on metadata failure) and
best-effort deletes, accepting orphan blobs but never dangling metadata.

## Decision
Keep the legacy invariant, made durable:
1. **Upload intent** row (PENDING) precedes any Blob write; capability is issued against
   the intent; callback verification promotes it to COMMITTED in the same transaction as
   metadata insert.
2. **Transactional outbox** rows are written inside the business transaction for every
   side effect (thumbnail job, e-mail, replication, cleanup).
3. **Cleanup worker** deletes Blob objects for expired/aborted intents and orphans found
   by reconciliation — at-least-once with idempotent deletes.

## Alternatives
- 2PC/XA across MySQL+HTTP — rejected: Blob has no prepare phase; heuristic outcomes.
- Saga orchestration framework — rejected: three fixed steps don't justify an engine.
- Synchronous compensation only (legacy) — rejected for durability: a crash between blob
  upload and metadata write currently leaks silently (DEF-08); intents make leaks
  discoverable and reapable.

## Consequences
`upload_intents`, `outbox_messages`, `cleanup_tasks` tables (Task 9.4); workers lease with
`FOR UPDATE SKIP LOCKED`, exponential retry + jitter, dead-letter with operator replay
(Task 12.4).

## Failure modes
Worker crash mid-job → lease expiry, another worker resumes; effects are idempotent
(keyed by intent/outbox id). Poison message → dead-letter after N attempts, alert on
queue age (Task 12.5).

## Rollback
Business rollback aborts the transaction → no outbox row, no audit row, intent stays
PENDING and is reaped. No client-visible partial state exists.

## Single writer
Each outbox/cleanup row is processed under a single lease; MySQL row locks guarantee one
worker at a time.
