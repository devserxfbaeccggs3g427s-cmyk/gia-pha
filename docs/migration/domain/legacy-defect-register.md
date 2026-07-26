# Legacy Defect Register (Task 3.5)

Every observed legacy quirk or defect, classified as one of:

- **preserve** — behavior is depended on by the frontend or data; the Spring
  `Compatibility_API` must reproduce it exactly.
- **security-correct** — a security defect; Spring corrects it immediately, with a
  documented, flag-controlled compatibility fallback where a client could break.
- **V2-only** — behavior is undesirable but harmless; kept in `Compatibility_API`,
  corrected only in `V2_API`.

| ID | Area | Observed behavior | Classification | Disposition in Spring |
|----|------|-------------------|----------------|-----------------------|
| DEF-01 | Share links | Public share projection returns **full member records** (phone, email, address, biography) to anonymous holders of the token (`share/[token]/route.ts`) | **security-correct** | Spring serves a redacted public projection (name, dates, gender, generation, avatar). Feature flag `compat.share.full-projection` (default off) restores legacy output during shadow comparison only; removed at decommission. |
| DEF-02 | Envelopes | Success payload shape is inconsistent: raw JSON for most routes, `{ok:true,data}` for register / membership PATCH / media upload / share-link management | **preserve** | Frozen in the OpenAPI baseline; the compatibility layer maps per-operation. `V2_API` uses one uniform envelope. |
| DEF-03 | Members | Member DELETE returns HTTP 200 with a `DeleteMemberResult` body while every other DELETE returns 204 | **preserve** | Frontend consumes the body (undo/report UI). Kept as-is. |
| DEF-04 | Relationships | Historical data contains **reciprocal rows** for symmetric types (both `A→B` and `B→A`) created before canonical ordering existed | **preserve** (read side) | Reads dedupe via canonical edge set; the migration transformer collapses reciprocal rows into a single canonical row and records both source ids in the migration ledger. New writes always canonical. |
| DEF-05 | Relationships | Intra-spouse-component parent-child edges (spouse marked as parent of own spouse's component) are silently ignored by generation calculation | **preserve** | The generation algorithm port ignores them identically; migration flags them as anomalies but does not delete. |
| DEF-06 | Generation | All-cycle malformed graphs pick the first component (iteration order) as root generation 0 | **preserve** | Iteration order is made deterministic (lexicographic component representative) — identical output for all real data, total output guaranteed. |
| DEF-07 | Media | Legacy scalar `memberId`/`eventId` coexist with `memberIds`/`eventIds` arrays; both are read, unioned, and written back | **preserve** (API), migrate (storage) | MySQL stores link tables only; the compatibility serializer re-emits the scalar when exactly the legacy scalar was populated, so payloads remain byte-compatible. |
| DEF-08 | Media | Blob deletion after metadata deletion is best-effort; failures only logged → orphan binaries accumulate | **preserve** semantics, improve ops | Same ordering guarantee (no dangling metadata). Spring adds a `cleanup_tasks` reconciliation worker so orphans are eventually removed — invisible to the API. |
| DEF-09 | Members | Merge re-appends the surviving member at the **end** of the collection, changing list order | **V2-only** | JSON-file ordering disappears with MySQL; compatibility list endpoints keep legacy default sort (insertion/createdAt) so pagination output stays stable. |
| DEF-10 | Members | No-op updates return the stored record and skip the changelog | **preserve** | Same short-circuit implemented (deep-equality on effective fields). |
| DEF-11 | Auth | Verify-email endpoint always 307-redirects to `/vi/login`, even on invalid/expired token (error carried in query param) | **preserve** | Exact redirect targets frozen in the OpenAPI baseline. |
| DEF-12 | Auth | Register returns 503 for any non-mapped `AuthServiceError` (e.g. email delivery failure), coupling account creation to SMTP availability | **V2-only** | Compatibility keeps 503. V2 creates the account and queues verification e-mail via the outbox (Req. 13). |
| DEF-13 | Events | Feb-29 anniversaries observed on Feb 28 in non-leap years | **preserve** | Deliberate product convention; golden fixture `events-upcoming-leap-day`. |
| DEF-14 | Search | Score cap at 100 makes prefix+fullName match indistinguishable from stronger multi-field matches | **preserve** | Scores are contractual (frontend sorts/labels by them). |
| DEF-15 | Backups | `+1 ms` probing on timestamp collision can theoretically loop 1000 times | **V2-only** | MySQL unique key + retry handles it; compatibility surface (timestamp identity of snapshots) unchanged. |
| DEF-16 | Backups | Backups/restore validate record `treeId` but not referential integrity between collections | **preserve** | Restore parity first; Spring adds a post-restore integrity report (log/audit only, no behavior change). |
| DEF-17 | Import | `.strict()` schemas reject any unknown field, including harmless ones exported by older app versions | **preserve** | Import is a validation gate; loosening silently would change accepted corpora. Documented in import help. |
| DEF-18 | Changelog | `userId` silently defaults to `'system'` when actor missing — attribution loss | **security-correct** | Spring requires an authenticated principal on every mutating compatibility route (legacy routes already do); `'system'` reserved for genuine system jobs (cron, workers). No client-visible change. |
| DEF-19 | Trees | Resource routes accept optional `?treeId=`; without it the handler scans all accessible trees to locate the resource (potential IDOR amplifier if authorization were per-route inconsistent) | **preserve** (behavior), harden (impl) | Same URL contract; Spring resolves the resource then enforces tree-scoped authorization centrally (Req. 16), so cross-tree probing returns 404 identically. |
| DEF-20 | Media | Thumbnail generation failure is silent (media exists without thumbnail; `thumbnailContentUrl` 404s) | **preserve** | Same tolerance; Spring adds a retryable `thumbnail` job so gaps self-heal. |

## Review

- Every register entry is linked from [`domain-semantics.md`](./domain-semantics.md) where
  the rule is described, and each `preserve` entry that is testable has a fixture in the
  [golden corpus](./golden-corpus/) or in
  [`../contract/golden-fixtures/golden-fixtures.json`](../contract/golden-fixtures/golden-fixtures.json).
- `security-correct` items (DEF-01, DEF-18) require sign-off in the threat model
  (Task 4) before implementation tasks 33 and 28 respectively.
