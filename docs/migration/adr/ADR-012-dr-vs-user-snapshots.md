# ADR-012: Infrastructure DR is separate from user-level snapshots

Status: Accepted · 2026-07 · Requirements: 11, 14.9–14.10

## Context
Legacy conflates one mechanism ("backups") for two needs: users restoring their tree to a
past state (30-day retention, self-service) and the platform surviving data loss
(RPO/RTO). MySQL brings real PITR, but the user-facing snapshot contract is frozen.

## Decision
Two independent mechanisms:
- **User snapshots** (product feature): per-tree JSON snapshot documents in Blob
  (`snapshots/{treeId}/…`) with metadata in MySQL; 30-day retention, timestamp identity,
  safety-snapshot-before-restore semantics — exactly the frozen legacy behavior.
- **Infrastructure DR** (ops): managed MySQL automated encrypted backups + binlogs
  (PITR, RPO ≤ 5 min, RTO ≤ 4 h) and Blob provider durability. Never exposed via API.

## Alternatives
- Serve user restores from MySQL PITR — rejected: PITR is database-global; a per-tree
  restore would leak/rollback other trees.
- Drop user snapshots since PITR exists — rejected: frozen API contract
  (`/api/backup/{treeId}`, 410 BACKUP_EXPIRED, retentionDays 30).

## Consequences
Restore of a user snapshot is an ordinary audited business transaction (delete/replace
tree content in MySQL), not an ops action; DR restores are runbooked ops procedures.

## Failure modes
Snapshot payload corrupt → validation identical to legacy (treeId/timestamp/record
ownership) rejects before any write; safety snapshot allows rollback of a half-applied
restore inside one DB transaction anyway.

## Rollback
User restore rolls back transactionally; DR restore rolls forward from binlogs.

## Single writer
Snapshots are written only by Spring's snapshot worker; DR artifacts only by the managed
database service.
