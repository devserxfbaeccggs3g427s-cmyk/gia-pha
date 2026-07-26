# Frozen Domain Semantics (Task 3)

Authoritative capture of every business rule implemented by the legacy Next.js backend.
The Spring `Compatibility_API` MUST reproduce these rules bit-for-bit unless the
[legacy defect register](./legacy-defect-register.md) explicitly classifies a rule as
`security-correct` or `V2-only`. Source references point at the legacy TypeScript that
defines each rule; the [golden corpus](./golden-corpus/) provides executable fixtures.

Requirement traceability: Req. 4–12, 18.1–18.2.

---

## 1. Members (`src/lib/services/member-service.ts`)

### 1.1 Date and status rules
- Dates are calendar dates parsed from the first `YYYY-MM-DD` prefix of the string, in UTC,
  with real-calendar validation (e.g. `2023-02-30` is invalid) — `parseCalendarDate`.
- `dateOfDeath < dateOfBirth` → `INVALID_INPUT` ("dateOfDeath cannot be before dateOfBirth").
- **A death date is authoritative**: on create, update and merge, `isAlive` is forced to
  `false` whenever `dateOfDeath` is present, regardless of input (`create` line 109,
  `update` line 150, `mergeMemberData` line 537).
- Derived status: `isAlive = !dateOfDeath && member.isAlive`; `status` is `ALIVE`/`DECEASED`.
- Lifespan = whole years between birth and (death | now), decremented if the anniversary has
  not occurred yet in the end year; clamped at ≥ 0; `null` when birth missing or end < birth.
- Update semantics: partial (`PUT` with partial body); `id`, `treeId`, `createdAt` immutable;
  date validation runs against the *effective* pair (`input ?? current`).
- **No-op update short-circuit**: if no field other than `updatedAt` changes (deep JSON
  equality), the stored record is returned unchanged and **no changelog entry is written**.
- Changelog `fieldChanged` on update is a comma-joined list of changed field names.
- `avatarMediaId` must reference media in the same tree with an `image/*` MIME type;
  otherwise `INVALID_INPUT`.

### 1.2 Deletion cascade (`deleteMember`)
Order and effects are contractual (response is `DeleteMemberResult`, HTTP 200 — not 204):
1. All relationships touching the member (either endpoint) are **deleted**.
2. Events referencing the member keep existing but have the member removed from
   `memberIds` (and get a fresh `updatedAt`). Events are never deleted.
3. Media linked to the member: the member is removed from `memberIds`/legacy `memberId`.
   Media whose **only** remaining link was this member (no other members, no events, no
   album) are deleted entirely — metadata first, then blobs best-effort (blob deletion
   failure is logged, not surfaced).
4. One `DELETE` changelog entry with `previousData` = full member snapshot.
- `GET …?preview=true` on the DELETE route returns `{member, affectedRelationships,
  affectedEvents, affectedMedia}` with **zero mutation**.
- `deletedRelationships` in the result is an alias of `affectedRelationships`.

### 1.3 Duplicate detection (`findDuplicates`)
- Name/place comparison uses Vietnamese normalization (NFD strip combining marks, `đ/Đ→d`,
  trim, `toLocaleLowerCase('vi')`); dates compare on the `YYYY-MM-DD` calendar key.
- Matching fields: `name` (fullName equality), `dateOfBirth`, `placeOfBirth`; `score` =
  matches/3.
- With `memberId`: pairs against that member require ≥ 1 matching field. Without: all-pairs
  require ≥ 2 matches, or ≥ 1 when explicit criteria were supplied. Unknown `memberId` → `[]`.

### 1.4 Merge (`mergeMember`)
- `sourceId === targetId` → `INVALID_INPUT`; missing either member → `NOT_FOUND`.
- Strategy aliases all resolve to one of `preferSource | preferTarget | nonEmpty`
  (`SOURCE_WINS`/`PREFER_SOURCE`/`{sourceWins}`/`{prefer:'source'}` → `preferSource`, etc.;
  default and unknown → `nonEmpty`).
- Field resolution (target is the survivor): `preferSource` takes source values that are not
  `undefined`/`''`; `nonEmpty` fills only empty target fields; `preferTarget` keeps target.
  `id`, `treeId`, `createdAt`, `updatedAt` never merge; `updatedAt` set to now;
  death-date-forces-dead re-applied.
- Relationships are rewired source→target, self-loops dropped, then deduplicated on
  `(source, target, type, customType)` keeping first occurrence.
- Events and media member references are rewired with de-duplication; legacy `memberId`
  scalar is rewired too.
- Survivor is re-appended at the **end** of the members collection (ordering side effect).
- Two changelog entries: `UPDATE` on target and `DELETE` on source, both `fieldChanged:
  'merge'`.

### 1.5 Media links on members
- `memberIds` (array) is canonical; legacy scalar `memberId` is still read and written for
  backward compatibility (`mediaMemberIds` unions both). Same for `eventIds`/`eventId`.

---

## 2. Relationships (`src/lib/services/relationship-service.ts`, `src/lib/algorithms/*`)

### 2.1 Canonical normalization
- Symmetric types `SPOUSE`, `SIBLING`, `CUSTOM` are stored with lexicographically ordered
  endpoints (`sourceMemberId <= targetMemberId`). `PARENT_CHILD` keeps direction
  (source = parent, target = child).
- Self-relationships are rejected (`INVALID_INPUT`). Both endpoints must exist in the tree.
- Duplicate = same canonical `(source, target, type)` (plus `customType` for `CUSTOM`) →
  HTTP 409 `DUPLICATE`.
- `marriageDate`/`divorceDate`/`marriageStatus` are only meaningful on `SPOUSE`;
  `divorceDate >= marriageDate` enforced.

### 2.2 Cycle detection (`cycle-detection.ts`)
- Only `PARENT_CHILD` edges participate. Inserting an edge parent→child is rejected with
  409 `CYCLE_DETECTED` when child already reaches parent via directed DFS over existing
  parent→child edges (i.e. the edge would close a directed cycle).
- Legacy reciprocal rows (both `A→B` and `B→A` stored historically) are tolerated in reads
  via canonical edge dedup (§2.4) but new inserts that create a 2-cycle are rejected.

### 2.3 Generation calculation (`generation.ts`)
1. Spouses are collapsed into components with a DisjointSet whose representative is the
   **lexicographically smallest member id** (stable, order-independent).
2. Parent→child edges are lifted to component edges; intra-component parent-child edges
   (spouse-of-ancestor corruptions) are ignored.
3. Components with indegree 0 are generation 0; BFS topological propagation assigns
   `child = parent + 1`; with multiple parents the **deepest constraint wins**
   (`candidate > current` replaces).
4. Malformed graphs with no root (all-cycle): first component (by iteration order) is
   forced generation 0 so output is total.
- A member's effective generation for reports: `member.generation ?? computed ?? 0`.

### 2.4 Ancestry (`ancestry.ts`)
- Canonical parent-child edge set dedupes reciprocal legacy rows before traversal.
- `getAncestryPath(memberId)`: BFS from generation-0 roots to the member; returns one
  shortest root→member path.
- `getAncestrySubgraph(memberId)`: all ancestors of the member (every parent branch),
  plus spouses of included ancestors, plus the connecting parent-child and spouse edges.

---

## 3. Events (`src/lib/services/event-service.ts`)

- Types: `BIRTHDAY` and `DEATH_ANNIVERSARY` are annual; others (`WEDDING`, `REUNION`,
  `CUSTOM`, …) occur once on `eventDate`.
- `memberIds`/`mediaIds` must all exist in the tree; unknown IDs → `INVALID_INPUT`.
- Upcoming query (`?upcoming=true&days=N`, default 30, cap 365): window is
  `[today, today+N]` in UTC calendar days; annual events project to the next anniversary
  (this year, else next year); one-time events use their literal date.
- **Leap-day rule**: a Feb-29 anniversary is observed on **Feb 28** in non-leap years
  (never skipped) — `annualDate`.
- `nextOccurrence` is a `YYYY-MM-DD` string, `daysUntil` = whole UTC days from today.
- Sort: `daysUntil` ascending, ties broken by `title.localeCompare`.
- Event DELETE returns 204 and also removes the event id from any media `eventIds`.

---

## 4. Vietnamese search (`src/lib/services/search-service.ts`, `src/lib/utils/vietnamese.ts`)

- Normalization pipeline (applied to both query and fields): Unicode NFD → strip combining
  marks (`\u0300-\u036f`) → `đ/Đ → d` → lowercase → trim → collapse internal whitespace.
  Result: accent- and case-insensitive matching (`nguyen van` matches `Nguyễn Văn`).
- Scored search (`?q=`): field weights — fullName 30, nickname 20, occupation 15,
  placeOfBirth 15, currentAddress 10, biography 10; +70 bonus when the normalized fullName
  **starts with** the query. Score capped at 100. Results sorted by score desc, then
  fullName asc (vi locale). `matchedFields` lists contributing fields.
- Autocomplete (`mode=suggest`): prefix matches on normalized fullName/nickname, max 10.
- Filter mode (`mode=filter`): `gender`, `generation`, `isAlive`/`status`, birth-year range.
  Supplying both `status` and `isAlive` with contradictory values → 400 `INVALID_FILTER`.

---

## 5. Import / Export (`src/lib/services/import-service.ts`, `export-service.ts`)

### 5.1 Import
- Formats: GEDCOM 5.5.x, JSON export document, CSV. Max upload 25 MiB (`MAX_IMPORT_BYTES`).
- Two-phase: `POST /api/import/preview` (parse + validate + issue list, no writes) then
  `POST /api/import/execute` (`mode=MERGE|REPLACE`).
- Validation uses `.strict()` Zod schemas — unknown fields are **errors**; IDs 1–300 chars;
  dates must `Date.parse`; the full record set is cross-checked (relationship endpoints,
  event member/media refs must resolve) producing `ImportIssue[]` with severity
  `ERROR|WARNING`. Any `ERROR` blocks execute.
- `REPLACE` swaps entire collections; `MERGE` merges by `id` with strategy
  (incoming wins / existing wins per `strategy`), collection by collection
  (members, relationships, events, mediaMetadata, albums).
- All imported records are re-stamped with the destination `treeId`.

### 5.2 Export
- Formats: `gedcom`, `json`, `pdf`, `png`, `svg`, plus `preview`; anything else → 400
  `INVALID_INPUT` "format must be GEDCOM, JSON, PDF, PNG, SVG, or preview".
- Download responses carry `Content-Disposition: attachment; filename="{treeId}.{ext}"`
  and `Cache-Control: private, no-store`.
- JSON export document embeds tree, members, relationships, events, mediaMetadata, albums
  and an export envelope (version, exportedAt) — this document is the import-JSON contract.

---

## 6. Media (`src/lib/services/media-service.ts`)

- Accepted: JPEG, PNG, WebP, PDF; max **10 MiB** (`MAX_MEDIA_SIZE`), 413 on overflow.
- **Magic-byte validation** must match the declared MIME (JPEG `FF D8 FF`, PNG 8-byte
  signature, WebP `RIFF….WEBP`, PDF `%PDF-`); mismatch → 415 `INVALID_FILE_TYPE` with the
  contractual Vietnamese message.
- Stored filename is `{id}.{ext}`; thumbnails: images only, sharp → WebP, width 480,
  quality 78, effort 4; thumbnail failure is non-fatal.
- Upload response is the **wrapped** envelope `{ok:true,data:MediaMetadata}` (201) with
  `contentUrl`/`thumbnailContentUrl` proxy URLs.
- Compensation: if metadata persistence fails after blob upload, uploaded blobs are deleted.
- Deletion order: metadata first, blobs best-effort afterwards — orphan blobs are
  acceptable, dangling metadata is not.
- Avatar upload additionally sets `member.avatarMediaId` and writes a member changelog
  entry with `fieldChanged: 'avatarMediaId'`.
- Listing sort: `takenAt ?? uploadedAt` descending. Only one of
  `memberId|eventId|albumId` filters may be supplied (400 otherwise, Vietnamese message).
- Content proxy headers (see OpenAPI baseline): originals
  `Cache-Control: private, max-age=3600`; derived variants
  `private, max-age=86400, stale-while-revalidate=604800` with
  `ETag "{sourceEtag}-{format}-w{width}-q{quality}"`; always `X-Content-Type-Options:
  nosniff` and RFC 5987 `filename*` dispositions.

---

## 7. Reports (`src/lib/services/report-service.ts`)

- Age is valid in `[0, 150]`; computed against death date when present, else today;
  averages rounded to 1 decimal.
- Age buckets: `0-17`, `18-30`, `31-45`, `46-60`, `61+`, `UNKNOWN`.
- Generation distribution uses `member.generation ?? computed ?? 0`.
- Branch statistics: branch = BFS descendants of the branch root **plus married-in spouses
  of those descendants** (spouses' own descendants via other marriages are not pulled in).
- Growth timeline: cumulative member count bucketed by `createdAt` month (`YYYY-MM`).
- Category maps are ordered by count desc, then label asc (vi locale), after NFD + vi-locale
  lowercase normalization.
- PDF rendering via pdf-lib is presentation-only; numbers must equal the JSON statistics.

---

## 8. Backups (`src/lib/services/backup-service.ts`)

- Retention **30 days**; snapshots keyed by ISO timestamp under `backups/{treeId}/`.
- One snapshot per UTC day via cron (`ensureDailyBackup`); manual `POST` allowed anytime.
- Timestamp collision → probe `+1 ms` (up to 1000 attempts).
- `listBackups`: hides entries more than 5 minutes in the future and expired ones; sorted
  newest first; response includes `retentionDays: 30`.
- Restore validation: timestamp > now+5 min → `INVALID_INPUT`; older than 30 days →
  410 `BACKUP_EXPIRED`; snapshot must match `treeId`/`timestamp` and every record must
  belong to the tree.
- Restore takes a **safety snapshot first**; on partial failure it rolls back from the
  safety snapshot (`Promise.allSettled` over collection writes).

---

## 9. Authentication (`src/lib/auth/*`)

- bcrypt cost 12; **dummy-hash comparison** on unknown email (timing defense).
- Lockout: 5 failed attempts → 15-minute lock; counter resets when a lock has expired.
- Email verification: token stored as SHA-256 hex; TTL 24 h; verify endpoint always 307
  redirects to `/vi/login` (`?verified=1` on success, `?error=…` otherwise).
- When verification is disabled by config, first successful login auto-verifies.
- Session idle timeout 30 minutes (NextAuth JWT strategy).
- Register: 201 wrapped envelope with contractual Vietnamese messages; 409
  `EMAIL_ALREADY_EXISTS`; 400 carries Zod `flatten()` details; 503 `EMAIL_DELIVERY_FAILED`.

---

## 10. Change logs (`src/lib/services/changelog-service.ts`)

- Append-only; `userId` defaults to `'system'`, `entityType` defaults to `MEMBER`.
- Entries carry `previousData`/`newData` full JSON snapshots and optional `fieldChanged`.
- No-op member updates write **no** entry (§1.1).

---

## 11. Share links (`src/lib/services/share-link-service.ts`)

- Token is an unguessable random ID; public endpoint requires no session.
- Responses: `Cache-Control: private, no-store` + `X-Robots-Tag: noindex, nofollow,
  noarchive`. Revoked/expired tokens → 404 (no existence oracle).
- Management endpoints use the wrapped `{ok:true,data:…}` envelope; delete → 204.
- The public projection currently returns full member records — see DEF-01 in the defect
  register (classified `security-correct`: Spring serves a redacted projection under a
  compatibility flag).
