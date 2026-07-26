# Technical Implementation Specification

## Migration to Spring Boot, MySQL, and Vercel Blob

| Attribute | Value |
|---|---|
| System | Family Genealogy Management API |
| Document status | Implementation baseline |
| Target release | Spring Boot 4.1.0, Java 25 LTS, MySQL 8.4 LTS |
| Architecture | Modular monolith using Hexagonal Architecture |
| Security baseline | OWASP ASVS 5.0 Level 2, OWASP API Security Top 10, least privilege |
| Migration pattern | Strangler migration with contract parity, shadow reads, and per-tree cutover |
| Structured data authority | MySQL |
| Binary object authority | Private Vercel Blob |
| Last reviewed | 2026-07-25 |

---

## 0. Executive Summary

The existing system is a Next.js 14 application whose backend consists of App Router route handlers. It stores users, trees, memberships, members, relationships, events, albums, media metadata, share links, change logs, and backups as whole JSON documents in private Vercel Blob. Binary originals and thumbnails are also stored in Vercel Blob. The current domain is mature, but the persistence model creates lost-update, transactionality, query-performance, referential-integrity, and operational-observability risks.

The target is a production-grade Spring Boot 4.1.0 modular monolith on Java 25 LTS. MySQL 8.4 LTS becomes the sole source of truth for relational and security data. Private Vercel Blob remains responsible only for binary objects and generated artifacts. The existing Next.js frontend remains in place during the migration and accesses Spring through same-origin routing or a backend-for-frontend proxy.

The design uses Hexagonal Architecture inside domain modules. HTTP, MySQL, Vercel Blob, email, authentication providers, and telemetry are adapters around application ports. Database mutations, audit entries, aggregate revisions, idempotency records, and outbox events commit atomically. External Blob operations use durable upload intents and cleanup jobs rather than pretending that MySQL and Blob can share a distributed transaction.

Vercel does not publish an official Java SDK. The production baseline therefore separates the Blob control plane from the data plane. A minimal control gateway on Vercel uses the official `@vercel/blob` SDK to issue short-lived, exact-path signed URLs and perform listing/copy operations. Browsers and Spring transfer binary bytes directly with signed URLs; they do not proxy 10 MiB payloads through a Vercel Function, whose request/response body limit is 4.5 MB. Spring accesses this integration through an authenticated outbound port and never holds a store-wide Blob token.

The migration avoids prolonged dual writes to existing JSON documents. It captures immutable source manifests, loads MySQL deterministically, runs shadow-read comparisons, freezes writes briefly for each tree, applies the final delta, reconciles all invariants, and then switches that tree to Spring as its single writer. Global identity has a separate single-writer cutover because users and OAuth accounts are not tree-scoped. Rollback remains available for a bounded period through versioned reverse projections and routing flags.

---

## 1. Purpose, Scope, and Guiding Principles

### 1.1 Purpose

This document is the definitive technical implementation guide for migrating the existing backend API to Spring Boot while preserving current business behavior, data, URLs, and frontend compatibility. It defines requirements, architecture, integration boundaries, data design, security controls, migration mechanics, validation gates, delivery sequencing, and Definition of Done for the engineering program.

### 1.2 In scope

- Existing authentication, registration, email verification, Google OAuth, and Facebook OAuth.
- Tree ownership, tree memberships, and `ADMIN`/`EDITOR`/`VIEWER` authorization.
- Family trees, members, relationships, genealogy algorithms, events, media, and albums.
- Search, filters, autocomplete, reports, imports, exports, backups, and public share links.
- Existing API route, payload, status, error, cache, redirect, and download behavior.
- Migration of all structured JSON data from Vercel Blob to MySQL.
- Continued private Vercel Blob storage for binary originals, thumbnails, and temporary generated artifacts.
- Security hardening, observability, CI/CD, deployment, disaster recovery, and legacy decommissioning.

### 1.3 Out of scope for the initial release

- Splitting the modular monolith into independently deployed microservices.
- Introducing Kafka, OpenSearch, Redis, or a service mesh without measured need.
- Redesigning the frontend user experience.
- Changing public API field names or status codes without an explicitly versioned API.
- Moving binary objects away from Vercel Blob.
- Native-image compilation unless a later performance study justifies it.

### 1.4 Guiding principles

1. **Preserve behavior before improving contracts.** Compatibility controllers preserve existing external behavior; improvements belong in a versioned API.
2. **One authoritative writer.** A tree is writable by either the legacy backend or Spring, never both concurrently. Global identity mutations likewise have exactly one authority at every migration stage.
3. **MySQL owns structured state.** Blob JSON is migration input and temporary rollback material, not target runtime persistence.
4. **Blob objects are immutable.** New object paths are unique; replacing a file creates a new object and retires the old one asynchronously.
5. **Authorization precedes lookup disclosure.** Every resource query is scoped by tree and principal.
6. **Transactions remain local.** External effects are coordinated through intents, outbox events, retries, and reconciliation.
7. **Security is a release gate.** OWASP ASVS Level 2 controls, dependency scanning, threat-model tests, and penetration testing are mandatory.
8. **Operational readiness is part of completion.** Health, metrics, tracing, alerts, backup restoration, and rollback must work before cutover.
9. **Use framework-managed versions.** Spring Boot dependency management is authoritative; arbitrary version overrides require an ADR and security review.

---

## 2. Current-State Assessment

### 2.1 Technology baseline

The current application uses Next.js, TypeScript, NextAuth, Zod, Vercel Blob, Sharp, PDF libraries, React Query, and Vitest as declared in `package.json:13`. Backend behavior is implemented under `src/app/api/**/route.ts`.

Authentication uses NextAuth JWT sessions with credentials, Google, and Facebook providers in `src/lib/auth/options.ts:9`. The session maximum age is 30 minutes. Tree authorization maps roles to permissions in `src/lib/auth/rbac.ts:4`.

Structured data and binaries share the Blob store. The complete storage path map is defined in `src/lib/blob/client.ts:26`. Structured writes overwrite a complete JSON collection with `allowOverwrite: true` in `src/lib/blob/client.ts:92`. This is last-write-wins and has no application-level transactional protection across concurrent requests.

The domain contracts are defined in `src/data/types.ts:11`, and validation limits are defined in `src/data/schemas.ts:41`. The current model includes:

- Users and OAuth accounts.
- Family trees with embedded memberships.
- Members and optional avatars.
- Canonical relationships and perspective views.
- Events with denormalized member and media links.
- Media metadata with legacy and current links.
- Albums.
- Change logs.
- Share links.
- Partial tree backup snapshots.

### 2.2 Existing functional surface

The migration must account for these API areas:

| Area | Existing capabilities |
|---|---|
| Authentication | NextAuth handlers, credentials login, registration, email verification, Google/Facebook OAuth |
| Trees | List, create, read, update, delete |
| Memberships | Assign or update role |
| Members | List, create, detail, update, deletion preview, cascade delete |
| Relationships | List, member perspective, create, validate, delete |
| Events | List, upcoming recurrence, create, detail, update, delete |
| Media | Upload, list/filter, metadata, authenticated content, responsive WebP, delete |
| Albums | List, create, update, delete |
| Search | Search, autocomplete, filters, Vietnamese accent normalization |
| Import | GEDCOM, JSON, CSV preview and execution |
| Export | GEDCOM, JSON, SVG, PNG, PDF, preview |
| Reports | Summary, branch statistics, timeline, PDF |
| Backups | List, create, restore, retention, daily cron |
| Sharing | Create/list/revoke links and public view-only tree projection |

### 2.3 Current business rules that must be preserved

- Tree owners have effective `ADMIN` authority regardless of membership contents.
- `ADMIN` can assign roles; `EDITOR` can mutate domain content; `VIEWER` is read-only.
- A member's death date cannot precede birth and implies `isAlive=false`.
- Avatar media must be an image in the same tree.
- Relationship endpoints must differ and exist in the same tree.
- Parent-child relationships cannot introduce a cycle.
- Symmetric relationships use stable endpoint ordering.
- Duplicate logical relationships are rejected.
- Divorce cannot precede marriage.
- Event member and media references must exist in the same tree.
- Birthdays and anniversaries recur annually; February 29 maps to February 28 in non-leap years.
- Uploads currently accept JPEG, PNG, WebP, and PDF up to 10 MiB, with magic-byte checks.
- Images are autorotated and receive a WebP thumbnail bounded to 480×480.
- Import accepts up to 25 MiB and supports append/replace plus skip/overwrite/regenerate conflict strategies.
- Share links are view-only, expire within 365 days, and can be revoked immediately.
- Application-level backups currently retain 30 days.

### 2.4 Current technical risks

| Risk | Impact | Target mitigation |
|---|---|---|
| Whole-array JSON overwrite | Lost updates under concurrency | Row-level transactions, unique constraints, optimistic locking |
| Multi-document compensation | Partial state when rollback fails | Single MySQL transaction for structured state |
| Read paths scanning all trees | Poor scaling and possible authorization leakage | Tree-scoped composite keys and indexed queries |
| Read path performs relationship migration writes | GET is not side-effect free | Explicit Flyway/data migration |
| Denormalized event/media links | Divergence and repair complexity | Single normalized join table |
| Best-effort binary cleanup | Orphan objects | Durable cleanup jobs and reconciliation |
| Partial backup payload | Incomplete restores | Versioned complete relational snapshot and infrastructure PITR |
| Inconsistent success envelopes | Client migration risk | Compatibility controllers and golden contracts |
| Sparse operational telemetry | Slow incident diagnosis | Structured logs, metrics, traces, SLO alerts |
| Browser service worker caches private API data | Cross-user privacy risk if cleanup fails | User-scoped cache versioning and mandatory purge on identity change |
| Concrete-looking secret in `.env.example` | Possible credential exposure | Rotate secret and replace with blank placeholder |
| No database constraints | Broken references and duplicates | FKs, checks, unique indexes, transactional validation |

---

## 3. Detailed Requirements Analysis

## 3.1 Functional requirements

### FR-001 API contract inventory and compatibility

- Create an OpenAPI baseline covering every existing method, route, path/query parameter, multipart field, body schema, success shape, error shape, status code, redirect, content type, cache header, and download header.
- Preserve existing `/api/**` URLs during the strangler migration.
- Preserve current mixed success styles where required by clients.
- Preserve the error envelope:

```json
{
  "ok": false,
  "error": {
    "code": "STABLE_CODE",
    "message": "Localized or compatibility message",
    "details": {}
  }
}
```

- Generate correlation IDs but do not add required client fields to the compatibility API.
- New API improvements use `/api/v2` and a consistent response format.

### FR-002 Authentication and identity

- Migrate credential users without password resets by preserving BCrypt hashes.
- Preserve registration validation: name 2–100 characters, normalized email up to 254, password 12–72 characters with upper, lower, numeric, and special characters.
- Preserve email verification and 24-hour token expiry.
- Preserve five failed attempts and 15-minute lockout.
- Preserve Google and Facebook OAuth account linkage.
- Preserve 30-minute idle session behavior.
- Provide logout and server-side revocation.
- During transition, validate a short-lived internal token issued after NextAuth session validation; do not decode NextAuth encrypted cookies directly in Spring.
- Final state uses Spring Security-managed opaque browser sessions or an approved OAuth2/OIDC authorization architecture.

### FR-003 Tree and membership management

- List trees owned by or shared with the current user.
- Create a tree and its owner `ADMIN` membership atomically.
- Return tree detail with required member and relationship projections.
- Update name and description with existing length limits.
- Delete a tree and schedule all associated binary cleanup.
- Assign `ADMIN`, `EDITOR`, or `VIEWER` to an existing user.
- Owner demotion is forbidden.
- Final tree deletion is restricted to the owner, even if legacy behavior allowed broader delete permission; expose this security correction behind a compatibility flag and document rollout.

### FR-004 Member lifecycle

- List and create members in a tree.
- Read member detail with relationships, related members, events, media, status, and lifespan.
- Update partial member data with optimistic concurrency in v2.
- Preview member deletion and return exact affected relationships, events, and media classifications.
- Delete members transactionally:
  - Delete related relationships.
  - Remove event links.
  - Remove member/media links.
  - Delete media metadata only when no remaining member, event, album, or avatar links exist.
  - Queue external object cleanup.
- Preserve duplicate detection and merge logic as application use cases, even if initially not exposed publicly.

### FR-005 Relationship and genealogy logic

- Store one canonical record per logical relationship.
- Support `PARENT_CHILD`, `SPOUSE`, `SIBLING`, `ADOPTED`, and `CUSTOM`.
- Preserve parent-to-child direction.
- Return member-perspective relationship roles.
- Reject self-reference, missing members, duplicates, invalid custom type, invalid marriage dates, and cycles.
- Port generation, ancestry, spouse-component, and cycle algorithms with property-based parity tests.
- Remove lazy migration writes from reads.

### FR-006 Events

- List events deterministically by date and title.
- Return upcoming recurring events for a validated 0–366-day window.
- Create, read, update, and delete events.
- Validate all member and media references in the same transaction.
- Use normalized join tables as the sole source of truth for event-member and event-media links.

### FR-007 Media and albums

- Continue private Vercel Blob storage.
- Accept JPEG, PNG, WebP, and PDF up to 10 MiB.
- Validate declared MIME, magic bytes, actual length, filename, and decoded image size.
- Create immutable, deterministic object keys under a tree namespace.
- Generate WebP thumbnails for image formats.
- Store path, ETag, SHA-256, size, content type, and lifecycle state in MySQL.
- Authorize before every metadata or binary read.
- Stream content; do not load unbounded content into heap.
- Preserve content options for thumbnail, download, WebP conversion, width, and quality.
- Support album CRUD; deleting an album detaches but does not delete media.
- Delete media through a database tombstone plus durable Blob cleanup job.

### FR-008 Search and filtering

- Preserve Vietnamese accent-insensitive matching, including `đ`/`Đ` normalization.
- Search full name, nickname, occupation, and birthplace.
- Preserve gender, generation, birth-year, alive/deceased, location, and field filters.
- Preserve autocomplete minimum prefix and deterministic order.
- Require `treeId` and cap result size.
- Initial implementation uses normalized columns, indexed SQL, and MySQL FULLTEXT where parity is demonstrated.

### FR-009 Import

- Accept multipart and JSON/base64 compatibility inputs.
- Support GEDCOM/GED, JSON, and CSV.
- Preserve 25 MiB decoded input limit.
- Preview counts, samples, warnings, and path/line errors without persistence.
- Execute append or replace with skip, overwrite, or regenerate conflict strategy.
- Parse outside the write transaction; commit validated relational state atomically.
- Use idempotency keys to prevent duplicate execution.
- Large future imports use asynchronous jobs and `202 Accepted` in v2.

### FR-010 Export and reports

- Export GEDCOM, JSON, SVG, PNG, PDF, and print preview.
- Preserve print options and safety limits, including 300 DPI minimum and image-pixel bounds.
- Generate statistics for complete trees and descendant branches.
- Preserve timeline and demographic calculations.
- Use a repeatable-read snapshot for coherent output.
- Stream synchronous results; use expiring private Blob artifacts for large asynchronous jobs.
- Embed a Vietnamese-capable font instead of transliterating unsupported characters.

### FR-011 Share links

- Create, list, and revoke view-only share links.
- Enforce future expiry up to 365 days.
- Store only a token hash for new links.
- Continue validating unexpired legacy v1 HMAC tokens during the migration window.
- Return 404 for invalid or unknown links and 410 for known expired links as required by compatibility fixtures.
- Treat removal of private member fields and raw Blob URLs from the current share response as an approved immediate security-breaking correction, not a parity exception.
- Use a versioned public projection allowlist; never expose owner IDs, memberships, private contact information, biography, notes, raw Blob URLs, or security metadata.
- Provide `GET /api/share/{token}/media/{mediaId}` or an equivalent explicit operation that validates token hash, expiry, revocation, tree scope, media association, and allowed media class before returning bytes.
- Public media operations never accept arbitrary Blob pathnames and use `private, no-store` or a separately approved short-lived private-cache policy.
- Public-share golden fixtures validate the corrected safe projection instead of reproducing legacy leakage.

### FR-012 Backup and restore

- Preserve list, manual create, restore, daily snapshot, and 30-day application retention.
- Snapshot all tree relational data, associations, metadata, schema version, record counts, and object manifest consistently.
- Create a safety snapshot before restore.
- Restore relational data in one transaction.
- Maintain managed MySQL full backups, encrypted binlogs, and PITR independently of application snapshots.
- Reconcile referenced Blob objects after restore.

### FR-013 Audit and idempotency

- Every successful mutation records an allowlisted audit representation with actor, action, tree, entity, timestamp, request ID, and trace ID in the same transaction.
- Password hashes, credentials, cookies, OAuth tokens, verification/share/session tokens, Blob capabilities, file bytes, and unnecessary sensitive PII are never included in audit payloads.
- Sensitive business values are redacted or field-level encrypted, access-audited, and governed by explicit retention and erasure rules; security-event audit and user-visible business history have separate schemas and retention.
- Failed or rolled-back operations do not produce a misleading business audit record.
- Retriable mutations accept `Idempotency-Key`.
- Replaying the same key and request returns the prior result; using the same key with another request hash returns 409.
- Offline PWA mutations preserve their idempotency key across retries.

### FR-014 Internal and future-v2 operations

- Duplicate-member detection and merge remain internal application use cases in the compatibility release; exposing them publicly requires a separate API contract covering authorization, request schema, conflict policy, idempotency, and audit.
- Audit-history retrieval is privileged and internal until its filtering, pagination, redaction, retention, and authorization contract is approved.
- Async import/export/report support in v2 must define job creation, status, cancellation, result download, expiry, ownership, and cleanup endpoints before implementation.
- No task-level capability may become a public endpoint without an approved OpenAPI operation and security review.

## 3.2 Non-functional requirements

### NFR-001 Availability and reliability

| Measure | Objective |
|---|---|
| API availability | 99.9% monthly, excluding approved maintenance |
| MySQL durability | Managed HA MySQL with multi-zone replication |
| MySQL RPO | ≤ 5 minutes |
| MySQL RTO | ≤ 60 minutes |
| Binary RPO | ≤ 24 hours through independent replication |
| Binary RTO | ≤ 4 hours |
| Cleanup jobs past `available_at + 24h` | 0 unresolved |
| Cross-tree unauthorized disclosure | 0 |

- Core structured operations continue when Blob is unavailable.
- Blob-dependent endpoints fail with bounded latency and stable temporary-unavailability errors.
- All workers are restart-safe, idempotent, and use durable leases.
- Objects intentionally retained until an approved rollback or legal-retention deadline use `RETENTION_HELD` and are not counted as overdue cleanup.
- Every active original is copied to an independently credentialed encrypted archive within the binary RPO. MySQL records replication state and checksum; restore drills recover data without depending on the primary Blob store.

### NFR-002 Performance

| Workload | Target |
|---|---|
| Normal read p95 | < 300 ms |
| Normal mutation p95 excluding upload | < 500 ms |
| Search/autocomplete p95 | < 200 ms |
| Blob content | First byte within 1 second at normal regional conditions |
| Outbox age p99 | < 60 seconds |
| Database pool utilization | < 80% sustained |

- Define maximum supported tree size from production profiling before implementation sign-off.
- No N+1 query path is accepted in primary endpoints.
- All list endpoints use bounded results or approved full-tree semantics.
- Import, export, image conversion, and report concurrency are bulkheaded.

### NFR-003 Scalability

- Spring API instances are stateless except for local bounded caches.
- Horizontal scaling must not create duplicate external effects.
- MySQL queries always include the tree partition key.
- Outbox workers use `FOR UPDATE SKIP LOCKED` and bounded batches.
- Redis and OpenSearch are introduced only after documented thresholds are exceeded.

### NFR-004 Maintainability

- Modules have explicit public ports and exclusive table ownership.
- Domain code contains no Spring, SQL, HTTP, Jackson, or Vercel types.
- ArchUnit enforces dependency direction.
- Flyway is the only production schema migration mechanism.
- OpenAPI, ADRs, runbooks, and threat models are versioned with code.
- Framework-managed dependencies are patched on a defined cadence.

### NFR-005 Security and privacy

- Target OWASP ASVS 5.0 Level 2.
- Apply OWASP API Security Top 10 controls.
- Use TLS for every network path and private networking for MySQL.
- Store secrets only in a secret manager.
- Do not log credentials, cookies, OAuth tokens, verification/share tokens, Blob tokens, private URLs, file bytes, or member PII.
- Encrypt disks, database backups, and external backup objects.
- Use database least-privilege identities for migration, runtime, backup, and reporting.
- Apply data retention and deletion rules to genealogy PII and audit data.

### NFR-006 Observability

- Every request has trace ID and request ID.
- Emit structured JSON logs.
- Export Micrometer metrics and OpenTelemetry traces.
- Monitor HTTP latency/errors, database pool and slow queries, Blob operations, authentication events, worker lag, imports/exports, JVM, and migration parity.
- Metrics must not use user, tree, member, email, or pathname as high-cardinality labels.

### NFR-007 Testability

- Unit and property tests cover domain rules.
- Integration tests use the exact MySQL 8.4 container, not H2.
- Blob Gateway tests use deterministic stubs and a private staging store.
- Differential contract tests compare legacy and Spring behavior.
- Security tests cover IDOR, CSRF, injection, token tampering, file abuse, and privacy projections.
- Migration reconciliation must be automated and reproducible.

---

## 4. Professional System Architecture Design

## 4.1 Architecture style

The target is a **modular monolith**. It is one deployable Spring Boot artifact with strong internal boundaries. This gives the system local ACID transactions for its tightly coupled genealogy aggregate while avoiding premature microservice complexity.

Each module follows Hexagonal Architecture:

```text
Inbound adapter -> Inbound port -> Application service -> Domain model
                                             |
                                             v
                                      Outbound port <- Outbound adapter
```

Dependency rules:

1. Domain depends only on Java.
2. Application depends on domain and port interfaces.
3. Inbound adapters translate HTTP, scheduler, or worker input into application commands.
4. Outbound adapters implement repositories, Blob, email, and telemetry ports.
5. Infrastructure DTOs never leak into domain models.
6. Cross-module calls use published application ports, not another module's repository.
7. Reporting may use approved read-only SQL projections but cannot mutate another module's tables.

## 4.2 Target component topology

```text
Browser / PWA
      |
      | HTTPS, same origin
      v
Vercel Edge / Next.js frontend and temporary BFF
      |                         \
      | /api business routes    \ /api/auth during transition
      v                           v
Spring Boot API              NextAuth compatibility bridge
      |
      +---------- MySQL 8.4 LTS
      |
      +---------- Private Blob Gateway on Vercel
      |                 |
      |                 +---- official @vercel/blob SDK
      |                              |
      |                              v
      |                       Private Vercel Blob
      |
      +---------- Email provider
      |
      +---------- OpenTelemetry collector / metrics backend
      |
      +---------- Independent backup target
```

### Trust boundaries

- Browser input is untrusted.
- Edge-forwarded identity headers are untrusted unless cryptographically bound by the internal token.
- The Spring-to-Blob-Gateway channel uses TLS, audience-bound service authentication, timestamp, nonce, body digest, and replay protection.
- MySQL is reachable only from application and operations networks.
- Blob objects remain private and are never exposed as raw URLs.

## 4.3 Module decomposition

| Module | Responsibilities | Main owned tables |
|---|---|---|
| `platform-kernel` | IDs, clock, errors, pagination, idempotency API, principals | None |
| `identity-access` | Users, credentials, OAuth, verification, sessions, lockout | `users`, `oauth_accounts`, `verification_tokens`, `auth_sessions` |
| `tree-content` | Trees, memberships, members, relationships, events, media metadata, albums, all associations, graph algorithms | `family_trees`, `tree_memberships`, `members`, `relationships`, `events`, `event_members`, `media_objects`, `media_members`, `event_media`, `member_avatars`, `albums`, `album_media` |
| `binary-storage` | Signed Blob control, upload intents, quarantine, scanning, cleanup, replication, reconciliation | `upload_intents`, `file_cleanup_job`, `binary_replica` |
| `sharing` | Share-token lifecycle and safe public projections | `share_links` |
| `transfer` | Import, export, snapshots, restore | `import_jobs`, `tree_snapshots` |
| `reporting-search` | Search, autocomplete, reports, generated documents | Read projections, optional derived columns |
| `audit-operations` | Redacted audit, outbox, scheduled operations | `audit_log`, `security_audit_log`, `outbox_event`, `processed_command` |
| `app-bootstrap` | Security chain, configuration, wiring, Actuator, runtime | None |

All transactionally coupled tree-content tables belong to one module. Genealogy, calendar, album, and media-metadata capabilities are internal feature packages within that module. This permits member deletion, merge, event/media synchronization, import, and restore to remain one local transaction without cyclic module dependencies. `binary-storage` owns only external object lifecycle and coordinates through ports/outbox; it never owns relational content associations.

Recommended source layout:

```text
backend/
  pom.xml
  app-bootstrap/
  platform-kernel/
  identity-access/
  tree-content/
  binary-storage/
  sharing/
  transfer/
  reporting-search/
  audit-operations/
```

Module layout:

```text
<module>/src/main/java/.../<module>/
  domain/model/
  domain/service/
  domain/event/
  application/port/in/
  application/port/out/
  application/command/
  application/query/
  application/service/
  adapter/in/web/
  adapter/in/worker/
  adapter/out/mysql/
  adapter/out/http/
  config/
```

## 4.4 Technology baseline

| Concern | Selection |
|---|---|
| Runtime | Java 25 LTS |
| Framework | Spring Boot 4.1.0 |
| Web | Spring MVC |
| Security | Spring Security managed by Boot BOM |
| Persistence | Spring Data JDBC plus `JdbcClient` for projections |
| Schema | Flyway Core plus the Boot-compatible `flyway-mysql` database module |
| Database | MySQL 8.4 LTS, InnoDB |
| Validation | Jakarta Bean Validation |
| HTTP client | Spring `RestClient` |
| Resilience | Spring Retry or a Boot-compatible Resilience4j version only after compatibility verification |
| Mapping | Explicit mapping code; MapStruct only if current stable version passes Boot 4/Java 25 review |
| API specification | Springdoc only after Boot 4.1 compatibility verification; otherwise generate OpenAPI from contract tests/build tooling |
| Testing | JUnit Jupiter/Platform managed by the Spring Boot 4.1 BOM, AssertJ, Mockito, Testcontainers, WireMock; jqwik only after BOM compatibility verification |
| Architecture tests | ArchUnit |
| Observability | Actuator, Micrometer, OpenTelemetry |
| Build | Maven Wrapper, reproducible build settings |
| Packaging | OCI image, non-root runtime |

All exact dependency versions are resolved from the Spring Boot 4.1.0 BOM where possible. The team must not guess versions in advance; CI validates compatibility and vulnerability status on every update.

## 4.5 Core application ports

### Inbound ports

- `RegisterUserUseCase`
- `VerifyEmailUseCase`
- `AuthenticateUserUseCase`
- `ManageSessionUseCase`
- `ManageTreeUseCase`
- `AssignTreeRoleUseCase`
- `ManageMemberUseCase`
- `MergeMemberUseCase`
- `ManageRelationshipUseCase`
- `ValidateRelationshipUseCase`
- `ManageEventUseCase`
- `ManageMediaUseCase`
- `ManageAlbumUseCase`
- `SearchTreeUseCase`
- `ImportTreeUseCase`
- `ExportTreeUseCase`
- `GenerateReportUseCase`
- `ManageShareLinkUseCase`
- `ManageTreeSnapshotUseCase`

### Outbound ports

- Domain repositories scoped by tree.
- `BinaryObjectStore`.
- `EmailSender`.
- `PasswordHasher`.
- `SessionStore`.
- `OAuthIdentityProvider`.
- `AuditWriter`.
- `OutboxRepository`.
- `IdempotencyRepository`.
- `MalwareScanner`.
- `TelemetryPublisher`.
- `LegacyIdentityVerifier`.
- `LegacyProjectionWriter` during rollback window only.

## 4.6 MySQL logical data model

### Database conventions

- `ENGINE=InnoDB`, `ROW_FORMAT=DYNAMIC`.
- Human text and preserved external/API IDs use `utf8mb4`; external IDs use `utf8mb4_bin` for exact, case-sensitive identity.
- Internal primary/foreign keys use compact `BIGINT UNSIGNED`; APIs expose only unchanged external IDs.
- Each externally addressed table has `external_id VARCHAR(300) NOT NULL` and a unique scope constraint such as `(tree_key, external_id)`.
- Hashes are binary (`BINARY(32)` for SHA-256); provider IDs and object keys use exact binary collation with lengths established from profiling.
- Migration profiling blocks duplicate or overlength external IDs; valid existing IDs are never rejected or transliterated merely to fit an ASCII convention.
- Domain calendar values use `DATE` and Java `LocalDate`.
- Instants use UTC `DATETIME(6)` with explicit JDBC converters between `Instant` and UTC `LocalDateTime`. Connection/session timezone is verified as UTC, including tests under a non-UTC JVM/host.
- Every mutable aggregate has `version BIGINT UNSIGNED NOT NULL DEFAULT 1` and UTC `created_at`/`updated_at`.
- All migrations are Flyway forward migrations using expand/migrate/contract sequencing.
- Before Task 09 is approved, a Flyway-adjacent data dictionary must define every column's type, nullability, default, check, charset/collation, index order, and referential action. The summary below is not a substitute for executable DDL.

### Principal tables

#### `users`

- `user_key BIGINT UNSIGNED` primary key and `external_id VARCHAR(300)` unique.
- `email VARCHAR(254)` normalized unique; `name VARCHAR(200)`.
- `password_hash VARCHAR(255)` nullable for OAuth-only users.
- `image_url VARCHAR(2048)`, `email_verified_at`, lockout fields, version, timestamps, optional soft-deletion timestamp.

#### `oauth_accounts`

- Unique `(provider, provider_account_id)` and FK `user_key` with cascade.
- Provider check for Google/Facebook.
- Future provider tokens are encrypted with KMS-backed envelope encryption when persistence is required.

#### `family_trees`

- `tree_key BIGINT UNSIGNED` primary key and unique `external_id`.
- `owner_user_key` FK to users with restrict.
- Name up to 200, description up to 2,000, `revision BIGINT UNSIGNED`, version, timestamps.

#### `tree_memberships`

- Composite primary key `(tree_key, user_key)`.
- Role check `ADMIN|EDITOR|VIEWER`; index `(user_key, tree_key)`.
- Application invariant: owner has `ADMIN` membership.

#### `members`

- `member_key BIGINT UNSIGNED` primary key and unique `(tree_key, external_id)`.
- Fields and limits mirror `src/data/types.ts:56` and `src/data/schemas.ts:41`.
- Birth/death are `DATE`; checks enforce ordering and death/living consistency.
- Tree-first indexes cover display name and common filters; normalized search columns preserve Vietnamese behavior.

#### `relationships`

- `relationship_key BIGINT UNSIGNED` primary key and unique `(tree_key, external_id)`.
- Composite FKs `(tree_key, source_member_key)` and `(tree_key, target_member_key)` reference unique member keys in the same tree, so cross-tree endpoints are impossible at the database layer.
- Type/custom/marriage fields, canonical pair keys, logical uniqueness, and endpoint traversal indexes.
- Cycle prevention is an application transaction invariant enforced under the tree graph lock.

#### `events`, `event_members`

- Event surrogate key plus unique `(tree_key, external_id)`.
- Event date is `DATE`; type/custom checks apply.
- `event_members(tree_key, event_key, member_key, position)` preserves stable source order and uses composite same-tree FKs.

#### `media_objects`

- Media surrogate key plus unique `(tree_key, external_id)`.
- Original/thumbnail object keys, opaque ETags, independently computed SHA-256, MIME, size, caption, taken date/precision, uploader, scan result, replication state, lifecycle state, version, timestamps.
- Lifecycle includes `PENDING_UPLOAD|PENDING_SCAN|ACTIVE|DELETING|RETENTION_HELD|DELETED|FAILED|ORPHANED`.
- Unique object-key constraints prevent accidental overwrite.

#### Association tables

- Every association includes `tree_key` and uses composite FKs to unique `(tree_key, internal_key)` parent keys; database constraints make cross-tree links impossible.
- `media_members(tree_key, media_key, member_key, position)`.
- `event_media(tree_key, event_key, media_key, position)` as the sole event/media truth.
- `member_avatars(tree_key, member_key, media_key)`.
- `albums(album_key, tree_key, external_id, title, description, version, timestamps)`.
- `album_media(tree_key, media_key, album_key)`; one album per media under current semantics.

#### Security and operations

- `share_links`: external ID, tree key, token hash, token version, nonce, expiry, revoke timestamp, creator.
- `audit_log` and `security_audit_log`: separate immutable allowlisted/redacted records with distinct retention.
- `processed_command`: idempotency key, actor scope, request hash, status, cached response.
- `outbox_event`: aggregate, event type, allowlisted payload, state, retry lease.
- `upload_intents`: exact quarantine/final paths, expected limits/checksums/links, expiry, lifecycle.
- `file_cleanup_job`: object locator, expected ETag, state, attempts, reason, `available_at`.
- `binary_replica`: primary locator/checksum, archive locator/checksum, state, replicated timestamp.
- `import_jobs`: format/mode/strategy/status/checksum/counts/errors.
- `generated_artifact_jobs`: owner, tree, operation/format, normalized options hash, state, progress, cancellation, result object key/checksum, expiry, attempts, and errors.
- `tree_snapshots`: schema version, compressed payload/object locator, checksum, counts, retention.

### Referential action policy

- User ownership of trees: `RESTRICT`.
- User memberships/OAuth/sessions: `CASCADE`.
- Audit actor/uploader: `SET NULL`.
- Tree domain rows: `CASCADE` after durable file-cleanup records are created.
- Member links and relationships: `CASCADE`.
- Event link rows: `CASCADE`; media survives.
- Album links: `CASCADE`; media survives.
- External object deletion is never implemented by a database cascade.

## 4.7 Request flows

### Standard mutation

```text
1. Controller validates syntax and obtains authenticated principal.
2. Authorization service resolves tree role before resource disclosure.
3. Application service opens transaction.
4. Repository loads aggregate with required lock/version.
5. Domain service validates invariants.
6. Repository mutates rows and increments tree revision.
7. Redacted audit and outbox rows are inserted.
8. Transaction commits.
9. Controller maps result to legacy or v2 DTO.
10. Worker processes external effects idempotently.
```

Every command that can change the parent-child graph first locks the owning `family_trees` row with `SELECT ... FOR UPDATE`, then reads graph edges and validates the candidate mutation. The lock remains through relationship mutation, revision, audit, and outbox commit. Lock order is tree row first, then entity rows in stable internal-key order. Adversarial concurrent tests must prove that at most one transaction commits when two individually valid edges would form a cycle together.

### Read

```text
1. Authenticate or validate share principal.
2. Authorize tree scope.
3. Execute indexed DTO projection.
4. Key local cache by tree ID, tree revision, query, and parameters.
5. Derive ETag from revision and projection key.
6. Honor If-None-Match.
7. Return private/no-store cache policy according to endpoint class.
```

### Media upload

```text
Transaction A:
- Authorize CREATE.
- Validate requested associations.
- Allocate media ID and exact immutable quarantine/final paths.
- Insert PENDING_UPLOAD intent with expiry and upload constraints.

Control/data plane:
- Blob control gateway issues an exact-path, short-lived signed PUT URL.
- Browser uploads directly to the private quarantine path; no 10 MiB body crosses a Vercel Function.
- Completion callback and client claims are treated as untrusted.
- Spring obtains signed HEAD/GET capabilities and verifies object existence, size, MIME signature, SHA-256, image/PDF parser limits, and mandatory malware scan.
- Scanner outage fails closed; object remains PENDING_SCAN and inaccessible.
- Valid content is copied/promoted to the immutable final path and thumbnail processing uses separately scoped signed operations.

Transaction B:
- Revalidate intent, scan result, final object metadata, and links.
- Insert ACTIVE media metadata and join rows.
- Set avatar if requested.
- Complete intent and insert redacted audit/outbox.

Recovery:
- Reconciler finalizes valid stranded intents or deletes expired quarantine/orphan objects.
- Multipart/abandoned upload state is enumerated and cleaned up.
```

### Media deletion

```text
1. Transaction removes user-visible links or marks media DELETING.
2. Transaction inserts cleanup job/outbox event.
3. API returns success after commit.
4. Worker deletes private Blob objects idempotently.
5. Metadata becomes DELETED or is purged after retention.
6. Repeated failure alerts but does not re-expose the object.
```

### Import

- Stream and checksum the input.
- Parse, normalize, and validate outside the write transaction.
- Build deterministic source-to-target ID mapping.
- Stage errors and preview output.
- Acquire tree write lock/revision.
- Commit all accepted relational changes, audit, and outbox in one transaction.

### Report/export

- Open a read-only repeatable-read transaction.
- Read a consistent projection.
- Close transaction before expensive rendering when the immutable projection is complete.
- Stream result or persist an expiring artifact.

## 4.8 Vercel Blob integration design

### Selected design: control gateway plus direct signed data plane

Vercel provides an official JavaScript SDK but no official Java SDK. A minimal Vercel-hosted control gateway uses the official `@vercel/blob` SDK to issue Vercel Signed URLs and perform control operations such as paginated listing and server-side copy. Binary bytes are not proxied through the gateway because Vercel Functions limit request and response bodies to 4.5 MB, below the required 10 MiB media limit.

Control-gateway responsibilities:

- Authenticate the Spring service or Next.js server and authorize a previously created upload/download/cleanup intent.
- Call `issueSignedToken()` and `presignUrl()` for exact pathnames and the minimum operation: `get`, `head`, `put`, or `delete`.
- Restrict upload URLs by content-type allowlist, maximum 10 MiB, `allowOverwrite=false`, private access, and narrow expiry.
- Verify signed-upload completion callbacks with `BLOB_WEBHOOK_PUBLIC_KEY`, while treating callback/client metadata as untrusted until Spring verifies the object.
- Provide paginated listing and controlled copy/promotion operations for reconciliation and quarantine finalization.
- Return stable internal error codes independent of Vercel SDK exception classes.

Data-plane behavior:

1. Browser uploads directly to an exact private quarantine pathname using a short-lived signed PUT URL.
2. Spring consumes separately signed HEAD/GET URLs with `RestClient` to validate or stream an object.
3. Spring workers consume exact-path signed DELETE URLs or request a gateway-controlled deletion.
4. Signed capability URLs and client signing tokens are secrets: they are never logged, persisted in domain tables, or returned outside the authorized operation.
5. Public/share downloads either stream through an authorization-adjacent application endpoint or use an explicitly approved very short-lived exact-path signed GET URL.

### Gateway authentication

- Gateway on Vercel uses OIDC credentials to access Blob where available.
- Spring-to-gateway requests use asymmetric service authentication or HMAC credentials stored in both platforms' secret managers.
- Signed service input includes method, canonical path, timestamp, nonce, body SHA-256, issuer, and audience.
- Gateway rejects clocks outside a narrow window and replays within that window.
- Keys rotate without downtime through key IDs and overlap.

### Blob policies

- Private store only; immutable unique final keys and isolated quarantine prefixes.
- Server-defined pathnames, filenames, and extensions.
- One-year Blob cache for immutable originals/thumbnails; browser cache remains private and endpoint-controlled.
- Use opaque ETag only for conditional requests, never as a content checksum.
- Compute SHA-256 by streaming bytes or validating a trusted application checksum.
- Bounded retry only for idempotent operations; honor `Retry-After`.
- Mandatory malware scan before activation.
- Replicate active originals to an independently credentialed encrypted archive within the binary RPO.
- Alert on authentication, protocol, quota, replication, scan, or repeated 5xx failures.

### Contingency direct control adapter

A direct Spring control-plane adapter may replace the gateway only if Vercel publishes a supported Java SDK or stable HTTP contract for token issuance/list/copy. It must remain behind `BinaryObjectStore`, pass the same contract suite, retrieve any static read-write token from a secret manager, and receive ADR/security approval. Standard HTTP consumption of short-lived Vercel Signed URLs is part of the selected design and does not require a store-wide token in Spring.

## 4.9 Authentication architecture

### Transitional state

1. Keep `/api/auth/**` in NextAuth for existing JWT session validation.
2. Next.js validates its session server-side.
3. It mints a maximum five-minute internal JWT signed asymmetrically.
4. Claims include `iss`, `aud`, `sub`, `iat`, `exp`, `jti`, and authentication strength.
5. Spring acts as a resource server and pins issuer, audience, algorithm, and public key.
6. The browser never stores this internal token.

Identity has a separate global single-writer cutover:

1. Load and reconcile users, verification state, OAuth accounts, and lockout state into MySQL.
2. Before tree canaries, either replace the NextAuth Blob adapter with a MySQL-backed compatibility adapter or briefly freeze registration/account mutation, apply a final identity delta, and atomically route all identity mutations to Spring.
3. Existing NextAuth JWT sessions may remain valid through token exchange, but registration, OAuth linkage, email verification, lockout counters, and account updates have exactly one persistence authority.
4. Reconcile normalized emails, OAuth provider keys, verification state, and lockout state immediately after switch.
5. `data/users.json` becomes read-only rollback material; NextAuth/Blob and Spring/MySQL never mutate the same user independently.

### Final state

- Spring owns registration, email verification, credentials, Google/Facebook OAuth, lockout, and sessions.
- Use opaque server-side sessions with `HttpOnly`, `Secure`, `SameSite=Lax`, narrow domain/path, idle and absolute expiry, rotation after login, and revocation.
- Keep same-origin routing to minimize CORS exposure.
- Cookie-authenticated unsafe methods require CSRF tokens.
- Existing BCrypt hashes remain valid; rehash after successful login when policy changes.
- User-row locking prevents concurrent failed-attempt bypass.
- Verification and provider tokens are hashed or encrypted as appropriate.

## 4.10 Authorization design

| Role | Read | Create | Update | Delete content | Assign roles/share |
|---|---:|---:|---:|---:|---:|
| Owner | Yes | Yes | Yes | Yes | Yes |
| ADMIN | Yes | Yes | Yes | Yes | Yes |
| EDITOR | Yes | Yes | Yes | Yes | No |
| VIEWER | Yes | No | No | No | No |
| Public share | Approved projection only | No | No | No | No |

- Use method security plus application-service guards.
- Repositories require tree-scoped keys.
- Do not trust client-supplied role or tree headers.
- Authorization cache is short-lived and revision-aware.
- Security-sensitive membership changes invalidate applicable sessions/caches.

## 4.11 API versioning and compatibility

- `adapter.in.web.legacy` serves current paths and DTOs.
- `adapter.in.web.v2` introduces consistent envelopes, required tree scope, pagination, ETags, `If-Match`, and mandatory idempotency for queued writes.
- Golden fixtures compare status, payload, redirects, cookies, cache headers, content disposition, and binary metadata.
- Deprecation headers precede removal of optional `treeId` scans and legacy request aliases.

## 4.12 Caching and search

### Caching

Start with bounded Caffeine caches:

- Authorization: 15 seconds.
- Tree summaries: 30 seconds.
- Upcoming events: 30 seconds.
- Reports: 1–5 minutes.

Cache keys include `tree_revision`, making stale entries unreachable after mutation. Introduce Redis only when multiple-replica measurements show local caching is insufficient or when session scale requires it.

### Search

- Maintain normalized searchable columns in the member transaction and map `đ/Đ` explicitly.
- Autocomplete and filters use tree-first indexed normalized prefixes/columns.
- Legacy arbitrary-substring search may use a bounded tree-scoped scan initially when maximum-tree profiling proves the latency target; a BTREE index cannot accelerate a leading-wildcard match.
- MySQL FULLTEXT is not parity-equivalent until two-character terms, Vietnamese normalization, substring semantics, ranking, and `matchedFields` pass golden tests.
- If bounded scans violate the target, introduce an n-gram/search index behind `SearchTreeUseCase`; retain MySQL as source of truth.

## 4.13 Observability architecture

- Actuator exposes only required health/metrics endpoints on a protected management interface.
- Readiness verifies migrations, database access, and security configuration; temporary Blob failure does not make all structured APIs unready.
- Liveness only represents process/deadlock failure.
- Structured logs include timestamp, level, service, route template, request ID, trace ID, outcome, duration, and pseudonymous actor/tree references.
- OpenTelemetry propagates traces through Next.js/BFF, Spring, Blob Gateway, and email integrations.
- Dashboards cover SLOs, MySQL, Blob, workers, auth, migration parity, imports/exports, and JVM.

## 4.14 Deployment architecture

- Next.js remains on Vercel.
- Spring runs in containers close to managed MySQL.
- The same image may run in `api` and `worker` roles with different profiles.
- Containers run as non-root with read-only root filesystem, a bounded temporary volume, CPU/memory limits, graceful shutdown, and connection draining.
- MySQL uses private networking, TLS, encryption at rest, HA, automated backups, and PITR.
- Vercel environments and Blob stores/prefixes are separated by environment.
- CI promotes the same signed image digest from staging to production.

---

## 5. Security Specification

## 5.1 Security controls

### Identity and session controls

- Passwords use an adaptive password encoder. Preserve current BCrypt compatibility; upgrade work factor through rehash-on-login.
- Apply dummy hash verification for unknown credential accounts to reduce timing enumeration.
- Rate-limit login, registration, verification, and password-reset-like flows by IP class and account hash.
- Rotate sessions on authentication and privilege changes.
- Revoke all sessions on account compromise or security-sensitive identity change.
- Never put sessions or access tokens in browser local storage.

### Authorization and IDOR prevention

- Every domain query includes `tree_id` and authorization scope.
- Do not search globally by member/event/media ID in the target API.
- Compatibility fallback lookups, if retained temporarily, first determine candidate tree without returning data, authorize, and emit deprecation telemetry.
- Automated tests enumerate role × endpoint × ownership combinations.

### Injection and input controls

- Parameterized SQL only.
- Bean Validation plus domain validation.
- Strict enum parsing; reject unknown fields for security-sensitive inputs where compatibility permits.
- Bound JSON nesting, collection length, string size, request size, and multipart part count.
- Escape/sanitize generated SVG, HTML, filenames, and response headers contextually.
- Do not execute external commands for rendering or parsing.

### File security

- Validate extension, declared MIME, signature, actual size, image dimensions, and parser limits.
- Use server-generated object names.
- Sanitize `Content-Disposition` using RFC-compliant encoding.
- Isolate image/PDF processing with CPU, memory, pixel, timeout, and concurrency limits.
- Malware scanning is mandatory and fail-closed for every upload, especially PDFs and other active-document formats.
- Objects remain in a private quarantine prefix and `PENDING_SCAN` until signature/parser/image validation and scanning succeed; scanner outage never activates content.
- Persist scan engine/version, signature-set version, result, and timestamp as security metadata without logging file content.
- Serve user PDF/media with `nosniff`, restrictive CSP, frame denial, and safe content disposition.

### Transport and browser controls

- TLS everywhere and HSTS at edge.
- Same-origin default; strict allowlist if CORS is unavoidable.
- CSRF protection for cookie-authenticated unsafe methods.
- CSP, `frame-ancestors`, `X-Content-Type-Options`, `Referrer-Policy`, and `Permissions-Policy`.
- Private/no-store caching for auth, share, backup, export, and mutations.
- Service-worker caches are versioned per authenticated identity and cleared on logout/session loss/user switch.

### Secrets and cryptography

- Secrets in a managed secret store only.
- Separate secrets for session signing, internal bridge, Blob Gateway, OAuth, email, cron, and database.
- Support key IDs and overlap during rotation.
- Store verification/share/session tokens as SHA-256 hashes where lookup is needed.
- Encrypt provider refresh credentials and especially sensitive PII if required by policy.
- Immediately rotate the concrete-looking `NEXTAUTH_SECRET` in `.env.example:2` if it was ever used, and replace it with an empty placeholder.

### Supply-chain controls

- Dependabot/Renovate-equivalent update automation.
- SCA, SAST, secret scanning, container scanning, IaC scanning, and license checks.
- Generate CycloneDX SBOM.
- Pin container base image digest.
- Sign artifacts and preserve build provenance.
- No deployment with unresolved critical/high findings without approved time-bounded exception.

## 5.2 Security verification gates

- OWASP ASVS 5.0 Level 2 checklist mapped to tests/evidence.
- Threat model reviewed before implementation and before cutover.
- Automated IDOR, CSRF, injection, token, upload, and privacy tests.
- External or independent penetration test before full cutover.
- Share response forbidden-field test.
- No secret/PII leakage in logs, traces, metrics, errors, or OpenAPI examples.

---

## 6. Data Migration Design

## 6.1 Source-to-target mapping

| Source | Target |
|---|---|
| `data/users.json` | `users`, `oauth_accounts`, verification records |
| `data/trees.json` | `family_trees`, `tree_memberships` |
| `members.json` | `members`, `member_avatars` |
| `relationships.json` | `relationships` after canonical normalization |
| `events.json` | `events`, `event_members`, candidate event-media links |
| `media-metadata.json` | `media_objects`, media-member/event/album links |
| `albums.json` | `albums` |
| `change-logs.json` | `audit_log` |
| tree share-link index and token objects | `share_links` with token hash |
| backup JSON | `tree_snapshots` metadata or legacy retention archive |
| media originals/thumbnails | Remain in private Vercel Blob; metadata reconciled in MySQL |

## 6.2 Deterministic transformation rules

1. Preserve existing IDs and timestamps.
2. Normalize email by trim and lowercase; duplicate normalized emails require manual reconciliation.
3. Convert empty OAuth password hashes to `NULL`.
4. Deduplicate memberships by `(treeId,userId)` using source-order compatibility; force owner to `ADMIN`.
5. Normalize relationships using current canonical logic, retain first logical row, quarantine later duplicates, and block unresolved cycles.
6. Convert birth/death/event calendar strings to their valid leading `YYYY-MM-DD` value.
7. Stable-deduplicate event member IDs.
8. Build media-member links from current arrays followed by legacy scalar IDs.
9. Build event-media links from the union of both legacy directions and report disagreements.
10. Create avatar links only for existing same-tree image media.
11. Preserve legacy avatar URL as read fallback during compatibility period.
12. Preserve Blob URLs only for migration diagnostics; derive stable object keys and verify existence.
13. Hash complete share tokens; retain encrypted/raw compatibility material only for the bounded rollback period.
14. Transform legacy before/after data through the approved audit allowlist/redaction policy and reclassify album events currently represented as media changes. Any legally required raw source remains only in an encrypted, access-controlled migration archive with explicit expiry, never in runtime audit tables.
15. Quarantine malformed records rather than silently coercing or dropping them.

## 6.3 Extraction

- Enumerate all Blob prefixes with pagination.
- Capture pathname, opaque ETag, size, upload time, extraction time, record count, and schema variant from provider metadata.
- Never infer SHA-256 from ETag. Compute SHA-256 only by streaming bytes or validating an already trusted application checksum.
- Discovery estimates full-binary hashing transfer volume, operation cost, throttling, and duration before requiring it for every object.
- Copy structured JSON to immutable migration storage.
- Create a signed manifest.
- Record every source record in staging with source pathname, array index, and canonical hash.
- Extraction is read-only and repeatable.

## 6.4 Loading

- Load global users first.
- Load each tree in an isolated transaction.
- Use staging tables and a migration ledger keyed by source pathname and ETag.
- Load normalized entities and associations in dependency order.
- Run FK, unique, check, graph, and business-rule validation before commit.
- Failed trees leave no partial production data.
- Loading the same manifest is idempotent.

## 6.5 Reconciliation gates

Cutover requires:

- `source = accepted + quarantined + approved duplicate` for every collection.
- 100% ID/count reconciliation for accepted records.
- Zero orphan FK anti-join results.
- Owner admin invariant for every tree.
- Zero duplicate logical relationships.
- Zero parent-child cycles.
- Zero invalid date/living/marriage constraints.
- Zero metadata references to missing required originals, unless explicitly quarantined.
- Canonical graph hash parity.
- Search, upcoming events, reports, and sampled API output parity.
- Binary object count/size/checksum reconciliation by tree.
- Every discrepancy has an owner and approved disposition.

## 6.6 Cutover

1. Shadow eligible reads and compare normalized results.
2. Select an eligible tree cohort.
3. Freeze legacy writes for those trees.
4. Capture final ETags and changed objects.
5. Apply final delta.
6. Run full reconciliation.
7. Atomically mark trees as Spring-owned in routing control.
8. Unfreeze writes through Spring.
9. Observe SLOs and parity.
10. Continue a bounded reverse legacy projection only when rollback requires it.

## 6.7 Rollback

- Keep original JSON and binary objects immutable through the rollback window.
- Delay file cleanup until rollback retention expires.
- Maintain a reverse exporter that reconstructs current Blob JSON shapes and denormalized links.
- If Spring-only writes occurred, freeze writes, reverse-export, validate against legacy readers, then switch routing.
- Rollback may force reauthentication if final Spring sessions cannot map to NextAuth sessions.
- Do not drop MySQL or staging data after rollback; retain it for forensic reconciliation.

---

## 7. Granular Implementation Roadmap

Each task below is sequential unless dependencies explicitly permit parallel work. A task is complete only when all acceptance criteria are met and evidence is attached to the delivery record.

### Phase 0 — Discovery and baselining

#### Task 01 — Freeze the complete HTTP contract

**Technical description**

Inventory all current route handlers. Record method, path, query/body/form fields, authentication, role, validation, response schema, status, error code, redirect, cookie, cache, content type, and download behavior. Generate an OpenAPI baseline and sanitized golden fixtures. Map every frontend API call to an operation.

**Definition of Done**

- 100% of current HTTP handlers appear in the compatibility matrix.
- Each operation has a success fixture and fixtures for every reachable error class.
- Multipart and binary endpoints include header and byte metadata fixtures.
- No unidentified frontend `/api` request remains.
- OpenAPI is version-controlled and reviewed.

#### Task 02 — Profile all source data and Blob objects

**Technical description**

Implement read-only inventory tooling for all paths in `src/lib/blob/client.ts:26`. Measure object counts, bytes, records, ETags, checksums, malformed data, duplicate IDs/emails, broken links, legacy fields, backups, share objects, and orphan binaries.

**Definition of Done**

- Every object is classified as migrate, retain, quarantine, or delete after retention.
- Counts and byte totals reconcile with paginated Blob listings.
- All anomalies have severity and disposition.
- The tool makes no source writes.

#### Task 03 — Freeze domain behavior and golden datasets

**Technical description**

Extract genealogy, recurrence, search, deletion, merge, import, export, report, backup, and authorization rules from TypeScript. Build sanitized golden trees covering multiple parents, adoption, spouse groups, cycles, leap-day events, Vietnamese names, legacy reciprocal rows, media links, and malformed imports.

**Definition of Done**

- Every critical rule has executable fixtures.
- TypeScript produces recorded expected outputs.
- Legacy defects to preserve or correct are explicitly approved.
- No business-critical behavior remains undocumented.

#### Task 04 — Approve NFRs, data classification, and threat model

**Technical description**

Profile production-like latency, scale, and object sizes. Classify identity data, family PII, media, share data, audit data, and backups. Threat-model trust boundaries and attacks, then approve SLO, RPO/RTO, retention, residency, and capacity thresholds.

**Definition of Done**

- Signed SLO/capacity table exists.
- Threat model covers IDOR, CSRF, injection, SSRF, malicious files, token leakage, account takeover, import bombs, and backup exfiltration.
- Security controls are mapped to implementation tasks and tests.

### Phase 1 — Architecture decisions and foundations

#### Task 05 — Record target architecture ADRs

**Technical description**

Approve modular monolith, Hexagonal Architecture, MySQL authority, Blob control gateway plus signed data plane, Spring MVC, Spring Data JDBC/JdbcClient, transactional outbox, upload intents, strangler routing, authentication bridge, caching, and backup separation.

**Definition of Done**

- ADRs include alternatives, rationale, consequences, diagrams, failure modes, and rollback.
- Module and table ownership is unambiguous.
- A single writer is identifiable in every migration state.

#### Task 06 — Scaffold Spring Boot 4.1.0 on Java 25

**Technical description**

Create Maven reactor modules, Java 25 toolchain, Boot 4.1.0 BOM, Web MVC, Security, Validation, JDBC, Flyway, Actuator, test infrastructure, formatting, static analysis, and architecture rules.

**Definition of Done**

- Wrapper build succeeds only on the approved toolchain.
- Application starts with no business endpoints.
- Architecture tests reject forbidden dependencies.
- Liveness/readiness endpoints are protected and functional.
- Dependency convergence contains no unmanaged Spring/Jakarta conflict.

#### Task 07 — Implement typed configuration and secret management

**Technical description**

Define validated properties for MySQL, auth, OAuth, Blob Gateway, email, cron/workers, upload limits, routing, and telemetry. Integrate the target secret manager. Remove unsafe defaults and rotate the exposed example secret.

**Definition of Done**

- Production startup fails on missing mandatory configuration.
- All secrets are redacted from logs and Actuator.
- Secret rotation works through rolling deployment.
- `.env.example` contains no concrete credential.

#### Task 08 — Provision exact MySQL 8.4 environments

**Technical description**

Provide local/Testcontainers and managed staging/production MySQL 8.4 with UTC, strict SQL mode, approved collation, TLS, private networking, HA, least-privilege users, and HikariCP limits.

**Definition of Done**

- Integration tests and staging use MySQL 8.4 exactly.
- Runtime identity cannot alter schema.
- Migration, runtime, backup, and reporting identities are separate.
- TLS and backup policies are verified.

#### Task 09 — Implement the complete Flyway schema

**Technical description**

Create all identity, domain, association, audit, idempotency, outbox, upload, cleanup, import, snapshot, and migration-ledger tables with FKs, checks, unique constraints, indexes, UTC timestamps, and version columns.

**Definition of Done**

- Empty database migrates from zero without manual action.
- Constraint tests cover every invariant expressible in SQL.
- Migration validation passes in CI.
- Query-driven index review is approved.
- Roll-forward repair procedure exists.

#### Task 10 — Build repository and transaction conventions

**Technical description**

Implement tree-scoped repositories, DTO projections, optimistic locking, batch operations, and standard transaction boundaries. Prohibit global resource scans in target code.

**Definition of Done**

- Repository tests prove cross-tree IDs cannot resolve.
- Primary query plans use expected indexes.
- No N+1 paths exist in golden scenarios.
- Injected failures leave no partial relational state.

#### Task 11 — Build compatibility/error/idempotency foundations

**Technical description**

Implement legacy serialization, error mapping, validation details, correlation IDs, cache/security headers, idempotency storage, and OpenAPI comparison tooling.

**Definition of Done**

- Common contract fixtures pass.
- Duplicate idempotent requests produce one mutation.
- Key reuse with a different request returns deterministic 409.
- SQL/internal exceptions never leak.

#### Task 12 — Implement audit, outbox, and durable workers

**Technical description**

Insert audit and outbox data in business transactions. Implement leased batch workers with retry, jitter, dead-letter state, deduplication, bounded concurrency, inspection, and replay controls.

**Definition of Done**

- Crash/restart tests prove at-least-once processing and idempotent effects.
- Rolled-back mutations have no business audit/outbox rows.
- Operators can inspect/retry jobs without direct SQL edits.
- Worker lag and dead letters are observable.

### Phase 2 — Blob and security platform

#### Task 13 — Implement the Vercel Blob control gateway

**Technical description**

Create a minimal Vercel Function using the official latest stable `@vercel/blob` SDK. Issue exact-path Vercel Signed URLs for GET/HEAD/PUT/DELETE, verify signed-upload callbacks, and implement authenticated list/copy control operations. Enforce private access, prefix allowlists, upload type/size, no-overwrite, narrow expiry, stable errors, OIDC Blob credentials, and service-key rotation. Do not proxy binary bodies through the Function.

**Definition of Done**

- Contract tests pass against a private staging Blob store.
- Unauthorized path/operation/type/size and malformed callbacks are rejected.
- A 10 MiB browser upload travels directly to Blob and never crosses the 4.5 MB Function body boundary.
- Exact-path capabilities expire and cannot authorize another operation or pathname.
- No read-write token, client-signing token, capability URL, or private raw URL appears in logs or persisted domain data.

#### Task 14 — Implement Spring `BinaryObjectStore`

**Technical description**

Create the outbound port and `RestClient` adapters for the authenticated control gateway and short-lived signed data-plane URLs. Add timeouts, retry policy, circuit breaker, conditional requests, error mapping, tracing, fake adapter, and WireMock fixtures.

**Definition of Done**

- Signed HEAD/GET/PUT/DELETE and gateway list/copy contracts pass from Spring.
- 401/403, 404, 409/412, 429, 5xx, timeout, expiry, and protocol failures map correctly.
- Non-idempotent blind retries are impossible.
- Gateway/service key rotation is demonstrated.

#### Task 15 — Implement upload intents, quarantine, and cleanup

**Technical description**

Build `PENDING_UPLOAD`/`PENDING_SCAN`/`ACTIVE` lifecycle, exact-path signed upload expiry, untrusted callback verification, mandatory malware scanning, checksum/parser validation, quarantine promotion, two-transaction finalization, multipart/abandoned upload cleanup, durable deletion, orphan sweeper, and retention-held deletion.

**Definition of Done**

- Failure at every upload/scan/promotion phase leaves recoverable state.
- Scanner outage fails closed and activates no object.
- Expired capabilities and abandoned quarantine/multipart objects are reconciled.
- No active metadata points to an absent or unscanned original.
- Deletion is externally idempotent; only jobs past `available_at + 24h` are alerted as overdue.

#### Task 16 — Apply platform security controls

**Technical description**

Configure Spring Security deny-by-default, CSRF, strict CORS/same-origin, secure headers, request limits, rate limits, redaction, TLS, secret management, and management endpoint isolation.

**Definition of Done**

- ASVS control mapping has implementation evidence.
- Automated CSRF, CORS, rate-limit, header, and redaction tests pass.
- SAST/SCA/secret/container scans have no unresolved critical/high finding.

### Phase 3 — Identity and authorization

#### Task 17 — Migrate identity persistence

**Technical description**

Implement users, OAuth accounts, verification tokens, lockout, normalized email, BCrypt compatibility, constraints, and migration transforms.

**Definition of Done**

- Existing golden hashes authenticate without reset.
- Duplicate email/account races are constraint-safe.
- User/OAuth migration counts and hashes reconcile exactly.

#### Task 18 — Implement transitional NextAuth token exchange

**Technical description**

Keep NextAuth routes for existing session validation, add server-only session exchange, issue asymmetric five-minute internal tokens, and configure Spring resource-server verification and key rotation.

**Definition of Done**

- Existing sessions authorize migrated APIs.
- Invalid issuer/audience/algorithm/signature/expiry/replay is rejected.
- Token never appears in browser storage or client logs.
- Bridge can be disabled by a kill switch.

#### Task 19 — Implement final Spring authentication

**Technical description**

Port registration, email verification, credential login, Google/Facebook OAuth, lockout, opaque sessions, logout, session revocation, and email outbox delivery without routing production identity mutations to it yet.

**Definition of Done**

- Contract and browser tests cover all success/failure/expiry flows.
- Existing users authenticate without password reset.
- Session fixation, CSRF, lockout race, key rotation, and revocation tests pass.
- Email outage does not corrupt registration state.

#### Task 19A — Execute global identity single-writer cutover

**Technical description**

Load and reconcile users/OAuth/verification/lockout data, apply a final identity delta, then atomically route all identity mutations to the completed Spring authentication implementation. Preserve existing NextAuth JWT session validation through token exchange while making Blob `users.json` read-only. A MySQL-backed NextAuth compatibility adapter is the only permitted intermediate writer if the cutover must be staged.

**Definition of Done**

- Registration, OAuth linking, email verification, lockout updates, and account changes each have exactly one writer in every migration state.
- Normalized email, provider keys, verification, and lockout state reconcile after switch.
- A concurrency test cannot mutate one user through both Blob and MySQL.
- Rollback procedure and identity RPO/RTO are rehearsed.

#### Task 20 — Implement tree authorization

**Technical description**

Centralize owner and role permissions, method security, public share principal, membership invalidation, and tree-first resource resolution.

**Definition of Done**

- Complete role × endpoint matrix passes.
- IDOR tests show zero cross-tree disclosure.
- Owner cannot be demoted.
- Public principal reaches only allowlisted read use cases.

### Phase 4 — Domain APIs

#### Task 21 — Migrate trees and memberships

**Technical description**

Implement tree list/create/detail/update/delete and membership role assignment with legacy DTOs, transactions, revisions, audit, and cleanup scheduling.

**Definition of Done**

- Legacy contract fixtures pass.
- Tree creation atomically creates owner admin membership.
- Concurrent role assignments remain unique.
- Tree deletion has durable cleanup records for every external object class.

#### Task 22 — Migrate members

**Technical description**

Implement member list/create/detail/update/deletion preview/delete, status/lifespan, avatar validation, duplicate detection, merge, transactional cascades, and audit.

**Definition of Done**

- Golden member fixtures match.
- Date and avatar invariants are enforced.
- Delete/merge failure injection rolls back relational state.
- Concurrent update returns deterministic optimistic-lock conflict in v2.

#### Task 23 — Migrate relationships and graph algorithms

**Technical description**

Port canonicalization, create/list/view/delete/validate, cycle prevention, generation, ancestry, spouse groups, and adoption behavior. Add locking and unique constraints for concurrency.

**Definition of Done**

- Java and TypeScript property tests agree on shared graph corpus.
- Reverse/symmetric duplicates cannot commit.
- Concurrent inserts cannot commit a cycle.
- Read operations perform no migration write.

#### Task 24 — Migrate events and recurrence

**Technical description**

Implement event CRUD, detail projection, upcoming recurrence, member/media associations, and legacy ordering/errors.

**Definition of Done**

- Event and leap-day fixtures match.
- Missing/cross-tree links fail before commit.
- Update/delete leaves no stale association row.

#### Task 25 — Migrate albums and media

**Technical description**

Implement media list/filter/detail, upload, authorized content, WebP variants, deletion, avatar behavior, and album CRUD using upload intents and Blob ports.

**Definition of Done**

- Valid JPEG/PNG/WebP/PDF flows pass.
- Spoofed, corrupt, oversized, decompression-bomb, and cross-tree files are rejected.
- Content bytes and approved headers match fixtures.
- Delete and orphan recovery pass injected-failure tests.

#### Task 26 — Migrate search, autocomplete, and filters

**Technical description**

Implement normalized Vietnamese columns, ranking, matched fields, prefixes, filters, pagination, and indexed SQL. Compare collation/fulltext behavior to golden data.

**Definition of Done**

- Membership, ordering, score, and matched fields match golden outputs.
- Query plans meet index policy at maximum test size.
- p95 search/autocomplete meets target.

#### Task 27 — Migrate audit history

**Technical description**

Port legacy change logs, add immutable security/business audit, correlation IDs, retention, privileged retrieval, and album reclassification.

**Definition of Done**

- Each committed mutation creates the required audit row exactly once.
- Normal runtime credentials cannot update/delete audit rows.
- Migrated audit counts and canonical hashes reconcile.

#### Task 28 — Migrate import preview and execution

**Technical description**

Port GEDCOM/JSON/CSV parsers, diagnostics, limits, reference/cycle validation, preview, append/replace, conflict strategies, deterministic remapping, staging, and idempotency.

**Definition of Done**

- Golden imports produce equivalent entities and diagnostics.
- Invalid/oversized/cyclic input creates zero domain changes.
- Replayed execution cannot duplicate data.
- Full tree mutation is atomic.

#### Task 29 — Migrate exports and print rendering

**Technical description**

Port JSON, GEDCOM, SVG, PNG, PDF, and preview options. Add Vietnamese font embedding, bounded rendering, safe XML/text, and streaming for compatibility requests within an approved synchronous size/time threshold. Large asynchronous artifacts are not enabled until Task 29A is complete.

**Definition of Done**

- JSON/GEDCOM parity passes after approved timestamp normalization.
- Visual outputs pass structural/image tolerances.
- Pixel/DPI, timeout, memory, and synchronous thresholds are enforced.
- Requests above the threshold return the approved compatibility error until the v2 job API is enabled.

#### Task 29A — Implement generated-artifact job API

**Technical description**

Define and implement versioned OpenAPI operations for export/report job creation, status, cancellation, authorized result download, and expiry. Add `generated_artifact_jobs`, worker leasing, normalized option/idempotency handling, private Blob result storage, retention, audit, and cleanup.

**Definition of Done**

- OpenAPI defines request/status/error schemas and authorization for every operation.
- Only the owner or an authorized tree principal can inspect, cancel, or download a job.
- Duplicate creation with the same idempotency key creates one artifact.
- Cancellation, expiry, cleanup, worker restart, and private result-download tests pass.

#### Task 30 — Migrate statistics and reports

**Technical description**

Implement summary, descendant branch, distributions, timeline, PDF, deterministic reference clock, SQL optimizations, and bounded computation.

**Definition of Done**

- Golden statistics match exactly for fixed clocks.
- PDF content and visual checks pass.
- Large-tree latency and memory meet NFR targets.

#### Task 31 — Migrate share links and public projections

**Technical description**

Implement create/list/revoke/resolve, legacy token verification, v2 opaque tokens, token hashing, key rotation, no-store/noindex, and a strict public DTO including scoped media access.

**Definition of Done**

- Valid, forged, expired, revoked, unknown, and rotated-key tests pass.
- Revocation is immediate.
- Automated forbidden-field scan detects no private PII, membership, owner, token, or Blob URL leakage.

#### Task 32 — Migrate snapshots, restore, and scheduling

**Technical description**

Implement complete tree snapshots, safety snapshot, atomic restore, retention, daily idempotency, object manifest, corrupt snapshot detection, and managed MySQL PITR procedures.

**Definition of Done**

- Snapshot round trip reproduces relational state exactly.
- Corrupt/wrong-tree/expired snapshots are rejected.
- Restore rollback works under injected failure.
- Daily snapshot is idempotent per UTC day.
- PITR and tree restore pass timed drills.

#### Task 32A — Implement independent binary replication and recovery

**Technical description**

Replicate every active original to an independently credentialed encrypted archive, store replication state/checksums in MySQL, reconcile inventory, isolate deletion credentials, and implement restore back to Vercel Blob. Thumbnails may be regenerated if approved.

**Definition of Done**

- Replication completes within the binary RPO and alerts on lag/failure.
- Archive credentials cannot delete or modify the primary store and primary credentials cannot erase the archive.
- Checksum reconciliation detects missing/corrupt copies.
- A timed restore drill meets binary RTO without reading the primary store.

#### Task 33 — Update frontend routing and PWA semantics

**Technical description**

Keep frontend URLs stable, proxy selected domains to Spring, add persistent idempotency keys to queued mutations, and version/clear private service-worker caches across auth/contract changes.

**Definition of Done**

- UI works in legacy, mixed, and Spring-only routing.
- Offline replay after ambiguous failure creates no duplicate mutation.
- Logout and user switch clear all prior private cache and queue state.

### Phase 5 — Verification and operational readiness

#### Task 34 — Port unit and property tests

**Technical description**

Port critical Vitest/fast-check suites to JUnit/jqwik. Share fixture files between TypeScript and Java during parity work.

**Definition of Done**

- All critical golden/property tests pass in both implementations.
- Approved branch coverage is met for domain modules.
- Mutation testing proves major invariants are detected.

#### Task 35 — Build MySQL and Blob integration tests

**Technical description**

Use Testcontainers MySQL 8.4, Blob Gateway stubs, staging Blob smoke tests, concurrency tests, and failure injection for transactions, locks, outbox, streaming, and cleanup.

**Definition of Done**

- CI tests are reproducible.
- No H2 substitute is used.
- Every known race and compensation failure has a regression test.

#### Task 36 — Enforce differential API contracts

**Technical description**

Replay sanitized fixtures against legacy and Spring. Compare status, payload, errors, redirects, cookies, caching, security headers, and binary metadata. Normalize only explicitly approved nondeterminism. Exclude the unsafe legacy public-share field set from parity and validate the approved safe projection instead.

**Definition of Done**

- Every operation passes or has an approved compatibility exception with expiry.
- Public-share field removal has a signed security-breaking-change record and forbidden-field test.
- OpenAPI diff contains no other unapproved breaking change.
- Frontend integration passes against both implementations.

#### Task 37 — Complete security and end-to-end testing

**Technical description**

Automate critical user journeys and IDOR, CSRF, injection, rate-limit, token, file, privacy, and backup attacks. Conduct independent penetration testing.

**Definition of Done**

- Critical journeys pass on the full staging stack.
- Zero cross-user/tree disclosure occurs.
- No unresolved critical/high security finding remains.
- ASVS Level 2 evidence is complete.

#### Task 38 — Load, resilience, and capacity testing

**Technical description**

Generate typical, p95, and maximum trees. Load-test reads, writes, search, file flow, imports, exports, reports, backups, database saturation, gateway throttling, and worker backlog. Tune pools, indexes, batches, timeouts, and bulkheads.

**Definition of Done**

- All SLO targets pass in staging.
- No unapproved full scan/N+1 appears.
- Dependency degradation produces bounded errors without pool/thread exhaustion.
- Capacity and autoscaling limits are documented.

#### Task 39 — Implement CI/CD and supply-chain controls

**Technical description**

Gate changes on build, format, static analysis, architecture, tests, OpenAPI, security scans, SBOM, container/IaC scans, signing, and provenance. Promote one immutable image.

**Definition of Done**

- Production image digest equals tested staging digest.
- All gates execute on pull requests and releases.
- Prior rollback artifacts remain available during compatibility window.

#### Task 40 — Implement observability and runbooks

**Technical description**

Add logs, metrics, traces, SLO dashboards, alerts, synthetic probes, on-call runbooks, migration parity dashboards, and privacy redaction.

**Definition of Done**

- A synthetic request traces from edge through Spring, MySQL, and Blob Gateway.
- Every SLO and cutover stop condition has a tested alert.
- Logs/traces contain no prohibited secrets or PII.
- Runbooks link alerts to exact diagnostics and remediation.

### Phase 6 — Migration, cutover, and decommissioning

#### Task 41 — Build immutable extraction and staging

**Technical description**

Implement read-only paginated extraction, manifests, raw immutable copies, checksums, schema detection, staging rows, and migration ledger.

**Definition of Done**

- Repeated extraction of unchanged data is byte/hash identical.
- Manifest accounts for 100% of source objects and records.
- Extraction high-watermark and anomalies are recorded.

#### Task 42 — Implement deterministic transformation and load

**Technical description**

Apply approved normalization rules, load users and trees transactionally, preserve IDs/timestamps, quarantine invalid data, and ensure idempotent reruns.

**Definition of Done**

- Same manifest can be loaded repeatedly without duplication.
- Committed trees have zero FK, uniqueness, cycle, or cross-tree violation.
- Failed tree load leaves no partial target state.

#### Task 43 — Build automated reconciliation

**Technical description**

Compare counts, IDs, canonical hashes, graph state, generations, events, search, reports, snapshots, and sampled binary bytes. Produce per-tree blocking/nonblocking discrepancy reports.

**Definition of Done**

- Cutover gate enforces zero blocking discrepancy.
- 100% accepted record reconciliation is achieved.
- Every accepted difference references an approved rule.
- Reports are retained as evidence.

#### Task 44 — Enable shadow reads

**Technical description**

Return legacy responses while asynchronously comparing eligible Spring reads. Redact comparison data, sample safely, and add kill switches.

**Definition of Done**

- Shadowing stays within approved overhead.
- Parity dashboards report endpoint/tree results.
- Kill switch works without deployment.
- Credentials and binary bodies are not unsafely shadowed.

#### Task 45 — Execute per-tree canaries

**Technical description**

Freeze selected trees, apply final delta, reconcile, atomically switch ownership, unfreeze through Spring, and observe. Keep bounded reverse projection for rollback.

**Definition of Done**

- Automated controls prove only one writer can own a tree.
- Canary observation period meets SLO/parity/security gates.
- Reverse projection lag remains below rollback RPO.

#### Task 46 — Rehearse cutover and rollback twice

**Technical description**

Use production-like data to rehearse extraction, freeze, final delta, switch, failure response, rollback, and second cutover. Simulate MySQL outage, gateway throttling, auth bridge failure, corrupt data, and worker backlog.

**Definition of Done**

- Two consecutive rehearsals meet RPO/RTO.
- Runbooks include commands, owners, validation, stop conditions, and communications.
- Application, DBA, security, and operations owners sign production readiness.

#### Task 47 — Deploy dark production

**Technical description**

Provision production infrastructure, deploy Spring with no user traffic, run Flyway, verify connectivity/security/resources, execute synthetic checks, and shadow read-only traffic.

**Definition of Done**

- Dark deployment survives the approved soak period.
- Security, contract, synthetic, and parity gates pass.
- Verified restore point and immutable source manifest exist.

#### Task 48 — Execute progressive production cutover

**Technical description**

Migrate internal trees, low-risk cohorts, and progressively larger cohorts. Automatically stop on discrepancy, security event, SLO breach, or projection lag.

**Definition of Done**

- 100% of active trees and business API domains are Spring-owned.
- Global reconciliation has zero blocking discrepancy.
- No structured production write goes to Blob JSON.

#### Task 49 — Stabilize and validate disaster recovery

**Technical description**

Run enhanced monitoring, resolve defects, tune queries/workers, verify scheduled snapshots, MySQL PITR, Blob reconciliation, and support outcomes through the stabilization period.

**Definition of Done**

- No unresolved severity-1/2 defect or sustained SLO breach remains.
- At least one post-cutover scheduled snapshot restore passes.
- PITR and binary recovery meet approved objectives.
- Business/security/operations approve completion.

#### Task 50 — Decommission legacy structured persistence

**Technical description**

After rollback expiry, remove Next.js business handlers, Blob JSON readers/writers, legacy cron, compatibility bridge when superseded, reverse projector, and structured-data credentials. Archive/delete source data under policy.

**Definition of Done**

- Runtime dependency analysis finds no structured Blob persistence call.
- Monitoring records zero legacy writes for the approved period.
- Obsolete secrets and infrastructure are revoked/removed.
- Retained data has an owner, legal basis, and deletion date.

#### Task 51 — Perform final post-migration audit

**Technical description**

Reconcile MySQL, retained binaries, snapshots, share state, audit history, and orphan inventory. Repeat penetration, dependency, restore, failover, and privacy assessments. Close ADRs and hand over operations.

**Definition of Done**

- Zero unexplained missing/duplicate record or broken active media reference.
- Zero active legacy credential.
- Zero unresolved critical/high security finding.
- Signed operational handover and decommission evidence close the program.

---

## 8. Delivery Governance

### 8.1 Definition of Ready

A task may start only when:

- Dependencies are complete.
- Contract and business behavior are known.
- Security/privacy impact is classified.
- Test data and acceptance criteria are available.
- Required ADRs are approved.

### 8.2 Global Definition of Done

In addition to task-specific criteria:

- Code follows module dependency rules.
- Unit, integration, contract, security, lint/static-analysis, and type/build checks pass.
- Flyway changes are reviewed and tested from an empty and previous-version database.
- Logs/metrics/traces contain no prohibited data.
- OpenAPI and runbooks are updated.
- Observability covers new failure modes.
- Rollback behavior is demonstrated.
- No critical/high vulnerability remains without approved exception.

### 8.3 Change control

- Breaking API changes require a versioned endpoint and migration notice.
- New external infrastructure requires an ADR and threat-model update.
- Dependency overrides require compatibility, license, and vulnerability review.
- Destructive database changes use expand/migrate/contract and occur only after rollback expiry.

### 8.4 Go/no-go criteria for production cutover

**Go only if:**

- Contract parity is within approved exceptions.
- Reconciliation has zero blocking discrepancy.
- Security gates and penetration test pass.
- Two migration/rollback rehearsals meet RPO/RTO.
- Backup and restore are proven.
- SLO load tests pass.
- On-call dashboards, alerts, and runbooks are active.
- Product, engineering, DBA, security, and operations owners approve.

**Stop or roll back if:**

- Any unauthorized cross-tree disclosure occurs.
- Data count/hash/graph discrepancy is blocking.
- Error budget or latency stop threshold is exceeded.
- Worker or reverse-projection lag exceeds rollback RPO.
- MySQL, auth bridge, or Blob integration enters an unrecoverable state.
- Restore or rollback evidence is invalid.

---

## 9. Key Architecture Decisions Summary

| ID | Decision |
|---|---|
| ADR-001 | Use a modular monolith rather than microservices. |
| ADR-002 | Use Java 25 LTS and Spring Boot 4.1.0. |
| ADR-003 | Apply Hexagonal Architecture inside each module. |
| ADR-004 | Make MySQL 8.4 LTS the only structured-data authority. |
| ADR-005 | Retain Vercel Blob only for private binary objects and generated artifacts. |
| ADR-006 | Use a Vercel Blob control gateway with the official JavaScript SDK and exact-path signed URLs; transfer binary bytes directly outside the Function body limit. |
| ADR-007 | Use Spring MVC with Spring Data JDBC and explicit SQL projections. |
| ADR-008 | Use transactional outbox, upload intents, and cleanup jobs; no distributed transactions. |
| ADR-009 | Preserve NextAuth through a short-lived token exchange, then migrate identity to Spring. |
| ADR-010 | Preserve legacy APIs through adapters and introduce improvements in v2. |
| ADR-011 | Use MySQL search and Caffeine first; add OpenSearch/Redis only from evidence. |
| ADR-012 | Separate infrastructure disaster recovery from user-level tree snapshots. |
| ADR-013 | Use per-tree single-writer cutover and avoid prolonged dual writes. |
| ADR-014 | Use explicit redacted public share DTOs. |
| ADR-015 | Treat tree revision as cache, ETag, and consistency token. |

---

## 10. Reference Baselines

- Current dependency baseline: `package.json:13`.
- Current domain contracts: `src/data/types.ts:11`.
- Current input limits and validation: `src/data/schemas.ts:41`.
- Current Vercel Blob paths and overwrite behavior: `src/lib/blob/client.ts:26` and `src/lib/blob/client.ts:92`.
- Current authentication configuration: `src/lib/auth/options.ts:9`.
- Current tree RBAC: `src/lib/auth/rbac.ts:4`.
- Current scheduled backup trigger: `vercel.json:1`.
- Spring Boot stable baseline verified for this specification: 4.1.0.
- Java support baseline: Java 25 is an LTS release.
- MySQL target: 8.4 LTS.
- OWASP security baseline: ASVS 5.0.0 Level 2.
- Vercel Blob security baseline: private storage requires authenticated reads/writes and is encrypted at rest; exact-path Signed URLs provide time-limited GET/HEAD/PUT/DELETE capabilities.
- Vercel Function constraint: request and response bodies are limited to 4.5 MB, so 10 MiB media uses direct signed uploads/downloads rather than Function proxying.
