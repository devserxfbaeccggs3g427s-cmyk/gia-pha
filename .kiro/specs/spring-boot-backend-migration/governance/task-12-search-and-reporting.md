# Task 12 — Implement Search & Reporting Service

## Scope

Search & Reporting owns MySQL read models for Vietnamese search,
autocomplete, statistics, and reports; coherent outputs use revision
barriers (`requirements.md:110`, `design.md:73,168,169`). The service
is read-only — it consumes projections from upstream domains and
publishes no cross-service events.

## Domain ownership

| Aggregate | Authoritative writes |
|---|---|
| `search_member_doc` | Search service only. Built from member events. |
| `search_event_doc` | Search service only. Built from event events. |
| `search_media_doc` | Search service only. Built from media events. |
| `autocomplete_entry` | Search service only. Derived from member/media/event docs. |
| `statistics_snapshot` | Search service only. Per-tree counts. |
| `report_snapshot` | Search service only. Per-tree per-kind report. |
| `watermark` | Search service only. Per-tree per-domain coherent watermark. |
| `member_generation_projection` | Search service only. Derived from member events. |

## Behaviour

### Vietnamese normalization (12.1)

- All string columns are stored with `utf8mb4_unicode_ci` collation.
- A `normalized_*` column is maintained alongside the surface form.
  Normalization lowercases, strips diacritics (NFC → NFD, remove
  combining marks), collapses whitespace, and maps common Vietnamese
  characters to ASCII equivalents (`đ→d`, `Đ→D`).
- Queries always pass through the application-layer normalizer so the
  database does not need to know about diacritics. LIKE comparisons
  are case-insensitive and accent-insensitive because of the
  collation; the normalizer keeps the surface text stable for
  display.

### Watermarks and revision barriers (12.2, 12.3)

- A `watermark` row stores the highest aggregate revision consumed
  per domain per tree.
- A `RevisionBarrier` aggregates the per-domain watermarks for a
  tree. Statistics and reports refuse to read when any required
  domain lags the requested watermark.
- When a query arrives with an `If-Match` watermark, the barrier is
  compared. If the barrier is below the requested watermark, the
  service returns `202 Accepted` with the current barrier in
  `Retry-After` headers and `watermarks` in the body.
- `StaleProjectionException` is returned when the projection has not
  yet caught up with the requested revision.

### Replay, rebuild, and reconciliation (12.4)

- Each projection has a domain consumer that maintains the
  watermark.
- `SearchMigrationController` exposes
  `POST /api/v2/internal/search/migration/projection/{domain}/rebuild`
  to replay events for a `(treeId, domain)` since the watermark,
  plus `GET /api/v2/internal/search/migration/reconcile` to enumerate
  drift.

### Statistics and reports (12.1, 12.5)

- Statistics are computed on-demand with a single SQL query against
  the read models. The result is cached in `statistics_snapshot`
  with the watermark observed.
- Reports are deterministic for a given watermark. Each `(treeId,
  kind)` row is upserted when the watermark advances. Duplicate
  computation for the same watermark is a no-op.

## Endpoints

- `GET /api/v2/search/members?treeId=...&q=...&birthYear=...`
- `GET /api/v2/search/events?treeId=...&q=...&from=...&to=...`
- `GET /api/v2/search/media?treeId=...&q=...&kind=...`
- `GET /api/v2/search/autocomplete?treeId=...&prefix=...`
- `GET /api/v2/search/statistics?treeId=...&watermark=...`
- `GET /api/v2/search/reports/{kind}?treeId=...&watermark=...`

All endpoints:

- require the caller to have a non-revoked authorization projection
  row for the tree;
- return the `watermarks` map in the body and in `X-Revision-Watermarks`
  headers so the gateway can issue `202` when the projection lags;
- return `202 Accepted` with the current barrier when the requested
  watermark has not yet been reached.

## Acceptance

- `search_member_doc`, `search_event_doc`, `search_media_doc`,
  `autocomplete_entry`, `statistics_snapshot`, `report_snapshot`,
  `watermark`, `member_generation_projection`,
  `authorization_projection`, `outbox_record`, `inbox_record`,
  `idempotency_record`, and `operation_audit` exist.
- All six endpoints above are wired and respect the watermark barrier.
- `SearchMigrationController` exposes loader and rebuild endpoints.
- A `VietnameseNormalizer` is unit-tested at the language level
  (not part of this task's repository gate).
