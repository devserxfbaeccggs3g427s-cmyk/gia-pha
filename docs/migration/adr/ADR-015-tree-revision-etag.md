# ADR-015: Tree revision drives ETag and cache consistency

Status: Accepted · 2026-07 · Requirements: 12

## Context
Legacy has no coherent invalidation: media variants derive ETags from Blob ETags, JSON
responses have none. With MySQL + Caffeine (ADR-011), the system needs one cheap signal
that "something in this tree changed".

## Decision
Every tree row carries a monotonically increasing `revision` (BIGINT), incremented in the
same transaction as **any** write to that tree's content. Uses:
- Caffeine keys: `(treeId, revision, projectionName)` — stale entries become unreachable,
  no explicit eviction logic.
- HTTP: V2 responses send `ETag: "t{revision}"` per tree-scoped resource collection and
  honor `If-None-Match` with 304. Compatibility responses keep their frozen header
  behavior (media variant ETag format unchanged).
- Cutover: revision comparison is a fast pre-check in reconciliation.

## Alternatives
- Per-entity `updatedAt` comparison — rejected: cross-collection consistency (member +
  relationships views) needs a single cursor.
- Event-driven cache invalidation — rejected: more machinery than a revision key with
  identical effect in-process.
- Content-hash ETags — rejected: requires rendering the body before deciding 304.

## Consequences
One extra UPDATE per write transaction (same row lock ordering: tree row last, Task 10.4);
revision gives free optimistic concurrency signal for the frontend if V2 clients want it.

## Failure modes
Hot tree row contention under write bursts → writes to one tree are already serialized by
business invariants; revision update shares that transaction, adding no new lock. Missed
increment (developer error) → ArchUnit/test rule: repository write methods must run
inside `TreeWriteTransaction` template which bumps the revision.

## Rollback
Dropping ETag emission is header-only; caches degrade to TTL semantics without
correctness loss.

## Single writer
Revision is only ever incremented by the stack that owns the tree per the migration
ledger (ADR-013).
