# ADR-004: MySQL is the only structured-data target authority

Status: Accepted · 2026-07 · Requirements: 12, 14, 19

## Context
Legacy stores all structured data as JSON arrays in Vercel Blob — no transactions, no
constraints, lost-update prone (whole-file PUTs). The migration needs referential
integrity (same-tree composite FKs), optimistic locking and PITR.

## Decision
After a tree/identity domain is cut over, MySQL 8.4 is the *sole* authority for all
structured data (users, trees, members, relationships, events, media metadata, albums,
changelogs, share links, snapshots metadata, audit, jobs). Blob JSON becomes read-only
input for migration/reconciliation and is never written by Spring.

## Alternatives
- Keep Blob JSON as source of truth with MySQL cache — rejected: perpetuates lost
  updates; two authorities violate the single-writer DoD.
- Document DB — rejected: the defect classes found by profiling (broken references,
  duplicate IDs) are exactly what SQL constraints eliminate.

## Consequences
Flyway owns schema (Task 9); every association gets same-tree composite FKs; migration
requires an extract-transform-load + reconciliation pipeline (Tasks 44–47).

## Failure modes
Drift between Blob and MySQL during a tree's shadow phase → reconciliation report gates
cutover; post-cutover legacy writes are blocked by routing (ADR-013).

## Rollback
Per-tree rollback re-points routing to legacy handlers whose Blob JSON was left untouched
(no prolonged dual writes, ADR-013); MySQL rows for that tree are quarantined, not
deleted, for re-cutover.

## Single writer
Pre-cutover: legacy Next.js writes Blob JSON. Post-cutover: Spring writes MySQL. The
migration ledger records the authority per tree at every instant; no state has two
writers.
