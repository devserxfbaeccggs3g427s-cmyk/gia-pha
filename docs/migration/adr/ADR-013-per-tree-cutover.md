# ADR-013: Per-tree cutover with no prolonged dual writes

Status: Accepted · 2026-07 · Requirements: 19, 20

## Context
Tree content (members, relationships, events, media metadata, albums, changelogs) is
naturally partitioned by `treeId`. A global big-bang cutover risks the whole product; a
long dual-write phase creates two writers and unresolvable conflicts.

## Decision
Strangler cutover **per tree**, driven by a `tree_migration_ledger` state machine:

```
LEGACY ─▶ COPYING ─▶ SHADOW(read-compare) ─▶ FROZEN(≤ minutes) ─▶ SPRING ─▶ VERIFIED
             │                │                    │  rollback ▼
             └────────────────┴──────────────── LEGACY (rows quarantined)
```

- COPYING: ETL loads the tree into MySQL while legacy keeps serving/writing.
- SHADOW: reads served by legacy, mirrored to Spring, responses diffed offline.
- FROZEN: brief write freeze (429 with `Retry-After` on writes to that tree), final delta
  copy + reconciliation, then routing flips.
- SPRING: Next.js `/api` routes for that tree proxy to Spring; legacy handlers refuse
  writes for cut trees.
- Dual writes never occur; the freeze window replaces them.

## Alternatives
- Global cutover — rejected: blast radius, unbounded freeze.
- Long-term dual writes with conflict resolution — rejected: two writers, silent
  divergence, contradicts DoD.
- Per-endpoint (not per-tree) strangling — rejected: one tree's data would span two
  authorities mid-request chains.

## Consequences
Routing needs a per-tree switch available to Next.js middleware (cheap ledger lookup,
cached with revision); reconciliation runs per tree with record-level SHA comparison;
rollback is per-tree and fast.

## Failure modes
Flip while requests in flight → freeze drains in-flight writes first; reads are safe in
both directions. Reconciliation mismatch → automatic rollback to LEGACY, mismatch report
attached to the ledger row. Ledger unavailable → routing fails closed to LEGACY.

## Rollback
`SPRING → LEGACY`: flip routing back (legacy Blob JSON untouched during SHADOW/FROZEN
except the final copy which never wrote to Blob); Spring rows quarantined for later
re-run. Documented drill required before first production cutover (Task 47).

## Single writer
The ledger names the writer (LEGACY | NONE-during-FROZEN | SPRING) for every tree at
every instant; both stacks enforce it on the write path.
