# ADR-005: Vercel Blob stores only private binary/artifact objects

Status: Accepted · 2026-07 · Requirements: 7, 19

## Context
Media originals/thumbnails, export artifacts and snapshot payloads are large immutable
blobs. Moving them into MySQL would bloat the database and PITR; moving to a new object
store would add a second migration with no user benefit.

## Decision
Vercel Blob remains the binary store, restricted to: `media/{treeId}/originals/*`,
`media/{treeId}/thumbnails/*`, `artifacts/{treeId}/*` (exports/reports), and
`snapshots/{treeId}/*` payloads. All objects private; all structured JSON leaves Blob
(ADR-004). Metadata (path, size, SHA-256, content type) lives in MySQL.

## Alternatives
- S3/GCS — rejected: second cloud account, IAM surface and egress path for zero
  functional gain during a parity migration.
- MySQL LONGBLOB — rejected: backup/PITR bloat, 10 MiB × concurrency memory pressure.

## Consequences
Binary access goes through the control gateway + signed URLs (ADR-006); orphan-object
reconciliation worker compares Blob listings against MySQL metadata (DEF-08).

## Failure modes
Blob outage → media/artifact endpoints degrade with stable 503 envelope while structured
API keeps working; queued uploads retry. Path-convention drift → gateway rejects
non-allowlisted path families.

## Rollback
The store is behind the `BinaryObjectStore` port; re-pointing to another provider is an
adapter change plus a background copy job (Task 35 replication tooling).

## Single writer
Spring (via gateway capabilities it issued) is the only writer of the new path families;
legacy `media/*` writes stop per tree at cutover, recorded in the migration ledger.
