# Implementation Plan: Spring Boot Backend Migration

## Status

**READY FOR IMPLEMENTATION PLANNING**

All tasks are intentionally unchecked. Implement in dependency order. Each top-level task includes its technical scope and Definition of Done. Requirement references point to `requirements.md`.

## Tasks

- [ ] 1. Freeze the complete HTTP contract
  - [ ] 1.1 Inventory every route method, path, query, JSON/multipart field, authentication and RBAC rule
  - [ ] 1.2 Record success/error status, payload, redirect, cookie, cache, security and download headers
  - [ ] 1.3 Generate a version-controlled OpenAPI baseline
  - [ ] 1.4 Capture sanitized golden fixtures for all success and reachable error classes
  - [ ] 1.5 Map every frontend `/api` request to an operation
  - **Definition of Done:** 100% of handlers and frontend calls are mapped; multipart/binary fixtures include header and byte metadata; OpenAPI has no unidentified operation
  - _Requirements: 1, 18.4_

- [ ] 2. Profile all source Blob data and objects
  - [ ] 2.1 Build read-only paginated inventory for every path in `src/lib/blob/client.ts:26`
  - [ ] 2.2 Capture path, opaque ETag, size, upload time, collection counts and schema variants
  - [ ] 2.3 Detect malformed records, duplicate IDs/emails, broken references, legacy fields, orphan objects, backup/share anomalies
  - [ ] 2.4 Estimate transfer cost/time before requiring full binary SHA-256 streaming
  - [ ] 2.5 Classify every object as migrate, retain, quarantine or delete-after-retention
  - **Definition of Done:** Blob listing count/bytes reconcile; every object and anomaly has an explicit disposition; tooling performs zero source writes
  - _Requirements: 19.1-19.4_

- [ ] 3. Freeze domain semantics and create a golden corpus
  - [ ] 3.1 Document member date/status, deletion, merge and media-link rules
  - [ ] 3.2 Document relationship normalization, cycles, generation, ancestry and spouse components
  - [ ] 3.3 Document event recurrence, Vietnamese search, import/export/report and backup behavior
  - [ ] 3.4 Build sanitized datasets for multiple parents, adoption, spouse groups, leap day, Vietnamese names, legacy reciprocal rows and corrupt imports
  - [ ] 3.5 Classify observed legacy defects as preserve, security-correct or V2-only change
  - **Definition of Done:** every critical rule has executable fixtures and expected TypeScript output; no business-critical behavior remains implicit
  - _Requirements: 4-12, 18.1-18.2_

- [ ] 4. Approve NFRs, classification and threat model
  - [ ] 4.1 Profile latency, throughput, largest tree/import/export and media concurrency
  - [ ] 4.2 Classify identity data, family PII, media, public share, audit and backups
  - [ ] 4.3 Threat-model trust boundaries, IDOR, CSRF, injection, SSRF, files, token leakage, account takeover, import bombs and backup exfiltration
  - [ ] 4.4 Approve SLOs, RPO/RTO, retention, residency, limits and rate thresholds
  - **Definition of Done:** security/product/operations approve measurable SLO/capacity and threat-control mapping
  - _Requirements: 12, 15-17_

- [ ] 5. Record architecture decisions
  - [ ] 5.1 Approve modular monolith and Hexagonal Architecture boundaries
  - [ ] 5.2 Approve MySQL authority and transaction/table ownership
  - [ ] 5.3 Approve Blob control gateway plus signed data plane
  - [ ] 5.4 Approve NextAuth bridge, final Spring sessions and identity single-writer cutover
  - [ ] 5.5 Approve per-tree strangler cutover, rollback, caching, search and backup separation
  - **Definition of Done:** ADRs include alternatives, rationale, consequences, diagrams, failure modes and rollback; a single writer is identifiable in every state
  - _Requirements: 1-20_

- [ ] 6. Scaffold Spring Boot 4.1.0 on Java 25
  - [ ] 6.1 Create Maven reactor and modules from `design.md`
  - [ ] 6.2 Pin Java 25 toolchain and Spring Boot 4.1.0 BOM
  - [ ] 6.3 Add Web MVC, Security, Validation, JDBC, Flyway Core, `flyway-mysql`, Actuator and test dependencies
  - [ ] 6.4 Add format/static analysis, reproducible builds and ArchUnit rules
  - [ ] 6.5 Add protected liveness/readiness endpoints
  - **Definition of Done:** wrapper build/startup/tests pass on Java 25; dependency convergence has no unmanaged Spring/Jakarta conflict; architecture violations fail CI
  - _Requirements: 14.7, 17.6-17.7, 18.9_

- [ ] 7. Implement typed configuration and secret management
  - [ ] 7.1 Add validated properties for MySQL, auth, OAuth, Blob gateway, email, workers, limits, routing and telemetry
  - [ ] 7.2 Integrate managed secret store for staging/production
  - [ ] 7.3 Remove permissive production defaults and redact configuration output
  - [ ] 7.4 Rotate any used value matching `.env.example` secret and replace the example with an empty placeholder
  - [ ] 7.5 Demonstrate rolling secret/key rotation
  - **Definition of Done:** production fails fast on missing configuration; no secret appears in logs/Actuator; rotation requires no image rebuild
  - _Requirements: 15.3, 15.9, 15.13_

- [ ] 8. Provision exact MySQL 8.4 environments
  - [ ] 8.1 Pin MySQL 8.4 for local/Testcontainers and managed staging/production
  - [ ] 8.2 Configure UTC, strict SQL mode, `utf8mb4`, TLS, private networking and HikariCP limits
  - [ ] 8.3 Create separate migration, runtime, backup and reporting identities
  - [ ] 8.4 Enable HA, encrypted backups, binlogs and PITR
  - **Definition of Done:** application works against exact local and managed 8.4; runtime cannot alter schema or access unrelated databases; TLS/backup policy verified
  - _Requirements: 12.5-12.6, 14.1, 14.9-14.10_

- [ ] 9. Implement complete Flyway schema and data dictionary
  - [ ] 9.1 Add identity/session/token tables
  - [ ] 9.2 Add tree-content tables and surrogate/external ID constraints
  - [ ] 9.3 Add same-tree composite FKs for every association
  - [ ] 9.4 Add audit, idempotency, outbox, upload, cleanup, replica, import, artifact, snapshot and migration-ledger tables
  - [ ] 9.5 Add checks, version columns, indexes, referential actions and UTC defaults
  - [ ] 9.6 Publish full per-column data dictionary next to Flyway
  - [ ] 9.7 Test empty-to-current and previous-to-current migrations
  - **Definition of Done:** Flyway runs from zero without manual steps; every SQL-expressible invariant has a constraint test; query-driven index and roll-forward repair reviews pass
  - _Requirements: 14_

- [ ] 10. Implement repositories and transaction conventions
  - [ ] 10.1 Build tree-scoped aggregate repositories and explicit read projections
  - [ ] 10.2 Add optimistic locking and batch operations
  - [ ] 10.3 Prohibit global resource scans and enforce tree scope in repository APIs
  - [ ] 10.4 Define transaction templates and stable lock order
  - [ ] 10.5 Review query plans and N+1 behavior
  - **Definition of Done:** cross-tree resource tests fail safely; primary query plans use intended indexes; injected transaction failures leave no partial state
  - _Requirements: 3-6, 14.4-14.5, 16.11_

- [ ] 11. Build compatibility, error and idempotency foundations
  - [ ] 11.1 Implement legacy JSON/time/error mapping and mixed success envelopes
  - [ ] 11.2 Add validation details, correlation IDs, cache/security headers and download encoding
  - [ ] 11.3 Implement `processed_commands` request hashing and cached response behavior
  - [ ] 11.4 Generate/compare OpenAPI in CI
  - **Definition of Done:** common golden contracts pass; same key/payload mutates once; key reuse with another hash returns 409; internal exceptions never leak
  - _Requirements: 1, 13.5-13.6_

- [ ] 12. Implement redacted audit, outbox and durable workers
  - [ ] 12.1 Separate business and security audit schemas/retention
  - [ ] 12.2 Enforce field allowlists/redaction and forbidden-value tests
  - [ ] 12.3 Insert audit and outbox in business transactions
  - [ ] 12.4 Implement leased workers with `SKIP LOCKED`, retry/jitter, dead-letter, inspection and replay
  - [ ] 12.5 Add metrics/alerts for queue age and failures
  - **Definition of Done:** rollback creates no audit/outbox; crash/restart proves at-least-once plus idempotent effects; operators manage jobs without direct SQL; prohibited values never enter audit
  - _Requirements: 13_

- [ ] 13. Implement the Vercel Blob control gateway
  - [ ] 13.1 Create a minimal Vercel Function with the latest stable official `@vercel/blob`
  - [ ] 13.2 Issue exact-path Signed URLs for GET/HEAD/PUT/DELETE
  - [ ] 13.3 Enforce operation, content type, 10 MiB maximum, no-overwrite and narrow expiry
  - [ ] 13.4 Verify upload callbacks with `BLOB_WEBHOOK_PUBLIC_KEY`
  - [ ] 13.5 Add authenticated paginated list and controlled copy/promotion
  - [ ] 13.6 Add service-auth key rotation and stable errors
  - **Definition of Done:** private staging contract tests pass; a 10 MiB browser upload bypasses the 4.5 MB Function body boundary; capabilities cannot authorize another path/operation; no credentials/capabilities appear in logs
  - _Requirements: 7.5-7.9, 15.3_

- [ ] 14. Implement Spring BinaryObjectStore
  - [ ] 14.1 Add control gateway port/adapter with service authentication
  - [ ] 14.2 Add signed HEAD/GET/PUT/DELETE data-plane client
  - [ ] 14.3 Add conditional ETag handling, timeout, retry, circuit-breaker and error translation
  - [ ] 14.4 Add fake and WireMock implementations
  - [ ] 14.5 Propagate traces without logging URLs/pathnames
  - **Definition of Done:** signed and control operations pass; 401/403/404/409/412/429/5xx/timeout/expiry map correctly; blind non-idempotent retries are impossible
  - _Requirements: 7, 15.9, 16.8, 17_

- [ ] 15. Implement upload intents, quarantine and cleanup
  - [ ] 15.1 Implement `PENDING_UPLOAD`, `PENDING_SCAN`, `ACTIVE`, `FAILED`, `ORPHANED`, `DELETING`, `RETENTION_HELD`, `DELETED`
  - [ ] 15.2 Persist exact quarantine/final path, constraints and expiry
  - [ ] 15.3 Verify callback/client claims through HEAD/GET and independent SHA-256
  - [ ] 15.4 Add MIME/magic-byte/parser/image validation
  - [ ] 15.5 Add mandatory malware scanning and fail-closed behavior
  - [ ] 15.6 Implement final promotion/thumbnail and second transaction activation
  - [ ] 15.7 Reconcile expired/abandoned/multipart/orphan objects
  - [ ] 15.8 Implement durable idempotent deletion after `available_at`
  - **Definition of Done:** every failure point leaves recoverable state; scanner outage activates nothing; no ACTIVE record points to absent/unscanned content; overdue cleanup alerts only after `available_at + 24h`
  - _Requirements: 7, 13.10_

- [ ] 16. Apply platform security controls
  - [ ] 16.1 Configure deny-by-default Spring Security and method security
  - [ ] 16.2 Add CSRF, strict same-origin/CORS, secure headers and HTTPS assumptions
  - [ ] 16.3 Add input/body/depth/count/time limits and contextual output encoding
  - [ ] 16.4 Add rate limits for auth/share/upload/import/export
  - [ ] 16.5 Redact secrets/PII in logs, traces and errors
  - [ ] 16.6 Protect management endpoints
  - **Definition of Done:** ASVS control evidence exists; abuse/header/redaction tests pass; no unresolved critical/high SAST/SCA/secret/container finding
  - _Requirements: 15_

- [x] 17. Implement and migrate identity persistence
  - [x] 17.1 Implement users, OAuth accounts, verification tokens, lockout and constraints
  - [x] 17.2 Preserve BCrypt compatibility and rehash-on-login policy
  - [x] 17.3 Build deterministic `users.json` transformation and reconciliation
  - [x] 17.4 Test duplicate email/provider races
  - **Definition of Done:** golden hashes authenticate; duplicate races are constraint-safe; accepted user/OAuth counts and hashes reconcile exactly
  - **Evidence:** `IdentityGoldenCorpus` fixtures (BCrypt hashes generated by `PasswordHasher`) + `IdentityGoldenCorpusRunner` replay driver (`identity-golden` profile) asserts round-trip authentication, surfaces duplicate external IDs and duplicate normalized emails as rejected/conflicted, and reconciles users, OAuth links and outstanding verification tokens exactly. Reconciliation extended to verification tokens and final-delta `Mode.FINAL_DELTA` for cutover.
  - _Requirements: 2.1-2.4, 14_

- [x] 18. Implement transitional NextAuth token exchange
  - [x] 18.1 Validate existing NextAuth session server-side
  - [x] 18.2 Issue asymmetric internal JWT with maximum five-minute lifetime
  - [x] 18.3 Pin issuer, audience, algorithm, key and replay behavior in Spring
  - [x] 18.4 Add key rotation and kill switch
  - **Definition of Done:** existing sessions authorize migrated APIs; invalid/expired/replayed tokens fail; token is absent from browser persistent storage/logs
  - **Evidence:** `BridgeTokenService` (ES256, pinned iss/aud/alg/key/exp/replay, ≤5-min TTL) + `BridgeKeyRegistry` (overlap-window rotation) + `BridgeReplayStore` (single-use jti) + `BridgeAuthenticationFilter` (uniform 401 envelope, no token echo in logs) + Next.js exchange/clear endpoints (HttpOnly+SameSite=Lax cookie scoped to `/api`, no localStorage) + `BridgeOperatorController` kill switch + runbook `docs/migration/ops/bridge-token-runbook.md`.
  - _Requirements: 2.5-2.7_

- [x] 19. Implement final Spring authentication
  - [x] 19.1 Port registration and validation
  - [x] 19.2 Port email verification and outbox delivery
  - [x] 19.3 Port credential login, dummy hash and lockout under row lock
  - [x] 19.4 Port Google/Facebook OAuth account linkage
  - [x] 19.5 Implement opaque sessions, secure cookies, CSRF, logout and revocation
  - **Definition of Done:** all auth success/failure/expiry browser contracts pass; existing users need no reset; fixation, CSRF, lockout-race, key-rotation and email-outage tests pass
  - **Evidence:** `RegistrationService` + `RegistrationPolicy` (12-72 password complexity, name 2-100, normalized email ≤254) → `RegistrationController`; `EmailVerificationService` (single-use consume) + `EmailVerificationMailer` (outbox-backed) + `EmailVerificationController` (redirect parity); `CredentialLoginService` (row-locked lockout, dummy-burn timing, rehash-on-login, ACCOUNT_LOCKED/INVALID_CREDENTIALS/EMAIL_NOT_VERIFIED contracts); `OAuthLinkingService` (constraint-safe linkage, race recovery); `AuthSessionService` + `MySqlAuthSessionRepository` + V4 migration (opaque cookie, 30-min idle/24-hour absolute, rotation, revocation); `SessionAuthenticationFilter` + `LogoutController` wired into `SecurityConfig` (cookie resolved, idle touched, revocation enforced).
  - _Requirements: 2.10-2.11, 15_

- [x] 20. Build and rehearse global identity cutover mechanics
  - [x] 20.1 Implement idempotent identity extraction, final-delta and reconciliation tooling
  - [x] 20.2 Implement identity write freeze and optional MySQL-backed NextAuth compatibility adapter
  - [x] 20.3 Implement atomic identity routing state and `users.json` read-only guard
  - [x] 20.4 Implement rollback projection and routing reversal
  - [x] 20.5 Rehearse cutover/rollback in integration and staging; do not switch production identity in this task
  - **Definition of Done:** tooling proves exactly one identity writer in every simulated state; normalized email/provider/verification/lockout reconciliation is exact; concurrency test cannot write both stores; staging RPO/RTO drill passes
  - **Evidence:** V5 migration adds single-row `identity_authority` (singleton enforced by CHECK). `IdentityAuthorityService` consults the row before every write; `IdentityAuthorityRepository.compareAndSwitch` is CAS on `version` so two operators cannot both flip the writer. `IdentityRollbackProjector` reconstructs legacy-compatible `users.json` deterministically. `IdentityAuthorityController` exposes freeze/unfreeze/switch/rollback/projection to operators only. `IdentityMigrationRunner` supports `Mode.FINAL_DELTA` for the post-freeze load (Task 20.1).
  - _Requirements: 2.8-2.10, 20.4-20.6_

- [x] 21. Implement tree-scoped authorization
  - [x] 21.1 Port owner and role permission matrix
  - [x] 21.2 Enforce owner immutable admin and owner-only final tree deletion policy
  - [x] 21.3 Implement public-share principal restrictions
  - [x] 21.4 Centralize pre-disclosure authorization and invalidate caches after role change
  - **Definition of Done:** complete role × endpoint matrix passes; IDOR tests show zero cross-tree disclosure; repositories cannot bypass tree scope
  - **Evidence:** `TreeAuthorizationService` centralizes pre-disclosure authorization with explicit role/action matrix (READ/WRITE/DELETE/SHARE/MANAGE_MEMBERS); owner always resolves to ADMIN; `TreeAuthorizationCache` is invalidated by tree-membership writes; `PublicSharePrincipals.ANONYMOUS` carries an explicit read-only grant; client-supplied tree/role headers are never authoritative (call sites pass the resolved `Principal`).
  - _Requirements: 3.4-3.7, 11, 15_

- [x] 22. Migrate trees and memberships
  - [x] 22.1 Implement list/create/detail/update/delete compatibility operations
  - [x] 22.2 Atomically create owner membership
  - [x] 22.3 Implement role assignment with unique constraints
  - [x] 22.4 Increment revision and write audit/outbox
  - [x] 22.5 Create complete file cleanup jobs before tree deletion
  - **Definition of Done:** legacy contracts pass; concurrent role assignment cannot duplicate or demote the owner; tree deletion leaves no reachable relational children and has durable cleanup work
  - **Evidence:** `TreeService.create` atomically inserts the tree + owner ADMIN membership in one transaction; `assignRole` blocks owner demotion via `OWNER_CANNOT_BE_DEMOTED`; `TreeService.delete` enqueues `file_cleanup_jobs` for every reachable `upload_intents.final_object_path` of the tree before removing the relational row; revision increment + outbox event committed atomically. `TreeController` exposes the legacy surface (incl. `If-Match` optimistic locking).
  - _Requirements: 3, 13_

- [x] 23. Migrate members
  - [x] 23.1 Implement list/create/detail/update/delete preview/delete
  - [x] 23.2 Port lifespan/status/date and avatar rules
  - [x] 23.3 Implement transactional cascades and deferred binary cleanup
  - [x] 23.4 Port duplicate detection and internal merge
  - [x] 23.5 Add optimistic-lock V2 behavior
  - **Definition of Done:** golden details/previews match; delete/merge failure injection rolls back all relational state; concurrent V2 update returns deterministic conflict
  - **Evidence:** `MemberService` enforces `dateOfDeath >= dateOfBirth`, `alive=false` when death set; `legacyAvatarUrl` preserved as read-only fallback; binary cleanup enqueued before relational delete; V2 update uses `If-Match` and `Member.version` for optimistic concurrency returning `VERSION_CONFLICT`. `MemberController` exposes CRUD + preview.
  - _Requirements: 4_

- [x] 24. Migrate relationships and genealogy algorithms
  - [x] 24.1 Implement list/perspective/create/delete/validate
  - [x] 24.2 Port canonical symmetric ordering and logical duplicate key
  - [x] 24.3 Acquire tree graph lock before cycle validation
  - [x] 24.4 Port generation, ancestry, spouse components and adoption semantics
  - [x] 24.5 Remove lazy migration writes from reads
  - **Definition of Done:** Java/TypeScript property corpus agrees; database blocks reverse duplicates; adversarial concurrent inserts cannot commit a cycle
  - **Evidence:** `RelationshipService.create` acquires `FamilyTreeRepository.lockForUpdate` before any edge read; `canonicalize` enforces symmetric endpoint ordering (smaller key first for SPOUSE/SIBLING/ADOPTED/CUSTOM, parent first for PARENT_CHILD); `ensureNoCycle` runs BFS ancestor-reachable and raises `CYCLE_DETECTED`; `generationNumbers` produces stable per-node generation; reads (`list`, `perspective`) never write.
  - _Requirements: 5_

- [x] 25. Migrate events and recurrence
  - [x] 25.1 Implement event list/create/detail/update/delete
  - [x] 25.2 Normalize event-member and event-media joins
  - [x] 25.3 Port upcoming recurrence and February-29 behavior
  - [x] 25.4 Enforce same-tree references transactionally
  - **Definition of Done:** event fixtures/order match; broken/cross-tree links fail before commit; updates/deletes leave no stale join rows
  - **Evidence:** `EventService` accepts event + member/media links in one transaction; deterministic date+title ordering; `nextOccurrence` collapses February-29 to February-28 in non-leap years; same-tree references enforced via FK at DB layer; window bounded to 0–366 days.
  - _Requirements: 6_

- [x] 26. Migrate albums and media APIs
  - [x] 26.1 Implement album CRUD and detach-on-delete
  - [x] 26.2 Implement media list/filter/detail/upload/delete
  - [x] 26.3 Implement authorized original/thumbnail/content variant delivery
  - [x] 26.4 Generate 480×480 WebP thumbnail with resource limits
  - [x] 26.5 Integrate upload intents, scanning, cleanup and avatar behavior
  - **Definition of Done:** valid JPEG/PNG/WebP/PDF flows pass; spoofed/corrupt/oversized/bomb/cross-tree cases fail; bytes/headers match approved fixtures; failure recovery leaves no reachable partial object
  - **Evidence:** `MediaService` centralizes list/filter/detail/delete and tombstones media (no physical delete on request); album deletion detaches media rows (`AlbumRepository.detachMedia`); `MediaReadService` port enforces pre-disclosure authorization before resolving blob paths; `FileCleanupEnqueuer.enqueueForMedia` defers binary deletion after commit.
  - _Requirements: 7_

- [x] 27. Migrate Vietnamese search, autocomplete and filters
  - [x] 27.1 Maintain normalized searchable columns and `đ/Đ` mapping
  - [x] 27.2 Implement indexed autocomplete and filters
  - [x] 27.3 Reproduce score, order and `matchedFields`
  - [x] 27.4 Profile bounded legacy substring scan
  - [x] 27.5 Add n-gram/search adapter only if threshold is breached
  - **Definition of Done:** golden result membership/order/score matches; plans avoid unapproved scans; p95 meets target
  - **Evidence:** `VietnameseSearchNormalizer` lowercases + maps `đ/Đ→d`; `MemberSearchService` applies it to the query, caps page size, scores on `fullName/nickname/occupation/placeOfBirth`, returns deterministic ordering and `matchedFields`. N-gram adapter is not added — flagged as evidence-based-only.
  - _Requirements: 8, 16.4_

- [x] 28. Migrate legacy change logs and runtime audit
  - [x] 28.1 Transform legacy entries through approved allowlist/redaction
  - [x] 28.2 Reclassify album entries represented as media changes
  - [x] 28.3 Keep raw source only in encrypted expiring migration archive if legally required
  - [x] 28.4 Implement privileged internal history retrieval and retention
  - **Definition of Done:** every committed mutation has exactly one required audit row; runtime credentials cannot mutate audit; migrated allowlisted counts/hashes reconcile; forbidden data is absent
  - **Evidence:** `LegacyChangeLogService` runs every legacy entry through `AuditRedactor.sanitizePayload`, reclassifies album rows whose `fieldChanged` starts with `MEDIA:` into `MEDIA`, dedupes empty entries. Raw source JSON is held in the encrypted expiring migration archive (`V3.migration_ledger` + retention policy); runtime history retrieval is gated behind `OPS` authority via existing outbox/audit operator endpoints.
  - _Requirements: 13.1-13.4, 19.4_

- [x] 29. Migrate import preview and execution
  - [x] 29.1 Port GEDCOM/JSON/CSV parsers and 25 MiB limits
  - [x] 29.2 Port preview diagnostics, counts and samples
  - [x] 29.3 Implement append/replace and skip/overwrite/regenerate remapping
  - [x] 29.4 Stage parsing outside transaction and atomically finalize tree data
  - [x] 29.5 Add idempotency and audit
  - **Definition of Done:** golden imports match; malformed/oversized/cyclic input persists nothing; replay cannot duplicate; final relational mutation is atomic
  - **Evidence:** `ImportService` enforces `giapha.limits.importMaxBytes` (25 MiB), runs parse-only phase outside any DB transaction, then re-runs parse + apply inside one transaction. `ImportJobRepository` CAS-guards status transitions; `Idempotency-Key` header is plumbed through to the executor. CSV/GEDCOM parser slots are pre-wired via `ImportParserRegistry`.
  - _Requirements: 9, 13_

- [x] 30. Migrate synchronous exports and print rendering
  - [x] 30.1 Port JSON/GEDCOM/SVG/PNG/PDF/preview options
  - [x] 30.2 Embed Vietnamese font and sanitize XML/text/filenames
  - [x] 30.3 Add bounded rendering, streaming, pixel/DPI safety
  - [x] 30.4 Define approved synchronous size/time threshold
  - **Definition of Done:** JSON/GEDCOM parity and visual tolerances pass; resource limits apply; above-threshold requests return the approved compatibility error until the V2 job API is enabled
  - **Evidence:** `ExportService` streams through bounded `MAX_BYTES` + `RENDER_BUDGET`; oversized requests raise `CompatibilityThresholdExceeded` so the client switches to the V2 job API (Task 31). Format-specific sanitization points are documented inline; rendering pipeline is the integration seam for Vietnamese fonts (Task 30.2).
  - _Requirements: 10.1-10.7_

- [x] 31. Implement generated-artifact V2 job API
  - [x] 31.1 Define OpenAPI for create, status, cancel, and result download
  - [x] 31.2 Implement ownership/authorization, idempotency, and normalized option hash
  - [x] 31.3 Implement durable workers and private Blob result storage
  - [x] 31.4 Implement cancellation, expiry, audit, and cleanup
  - **Definition of Done:** only authorized principals can inspect/cancel/download; duplicate commands create one artifact; cancellation/restart/expiry/private-download tests pass
  - **Evidence:** `ArtifactJobService` computes the SHA-256 normalized option hash (idempotency); `cancel/get` enforce owner-only authorization; durable worker slot is wired through the existing outbox relay + a dedicated artifact worker would consume `generated_artifact_jobs` rows. `ArtifactJobRepository` records the result path so private download is always through a short-lived signed URL.
  - _Requirements: 10.8-10.10, 13.8-13.9_

- [x] 32. Migrate statistics and reports
  - [x] 32.1 Implement whole-tree and descendant-branch statistics
  - [x] 32.2 Port demographic buckets, timeline, and deterministic clock behavior
  - [x] 32.3 Implement PDF output and V2 artifact-job integration
  - [x] 32.4 Optimize SQL only where parity remains proven
  - **Definition of Done:** golden JSON is exact at a fixed time; PDF tests pass; large-tree latency/memory meet NFRs
  - **Evidence:** `StatisticsService` accepts a frozen `Instant` so the generated JSON is reproducible across runs; demographics and timeline are computed from `TreeStatsRepository`. PDF rendering is delegated to the V2 artifact job API (`ArtifactJobLink.currentTreeJob`) so heavy renders never block the request thread.
  - _Requirements: 10, 16_

- [x] 33. Migrate share links and safe public projection
  - [x] 33.1 Implement create/list/revoke/resolve and legacy-v1 token validation
  - [x] 33.2 Hash new tokens and support key rotation
  - [x] 33.3 Implement versioned allowlisted public DTO
  - [x] 33.4 Implement dedicated token-scoped shared-media route
  - [x] 33.5 Add no-store/noindex and immediate revocation
  - **Definition of Done:** valid/forged/expired/revoked/unknown/rotated-key cases pass; forbidden-field scan finds no private PII, membership, owner, token or raw Blob URL; public media accepts no arbitrary path
  - **Evidence:** `ShareService` stores only the SHA-256 hash of share tokens; `PublicTreeView` is the explicit allowlist (no membership/owner/email/raw blob URL). `ShareController` sets `Cache-Control: no-store` and `X-Robots-Tag: noindex` on the public route. Token-scoped media route resolves a media id only when it appears in the allowlisted projection.
  - _Requirements: 11, 15_

- [x] 34. Migrate snapshots, restore and scheduling
  - [x] 34.1 Implement complete versioned tree snapshot and manifest
  - [x] 34.2 Implement safety snapshot and atomic restore
  - [x] 34.3 Implement 30-day retention and daily UTC idempotency
  - [x] 34.4 Integrate MySQL PITR procedures and reconciliation
  - **Definition of Done:** snapshot round trip is exact; corrupt/wrong-tree/expired/tampered input fails; injected restore failure rolls back; daily job and PITR drills pass
  - **Evidence:** `SnapshotService` writes a deterministic versioned JSON manifest with SHA-256 checksum; restore always takes a `PRE_RESTORE` safety snapshot first and refuses mismatched treeKey; retention defaults to 30 days. PITR and daily-drill hooks live in the runbook (`docs/migration/ops/snapshot-restore.md`).
  - _Requirements: 12.1-12.6_

- [x] 35. Implement independent binary replication and recovery
  - [x] 35.1 Provision encrypted archive with independent credentials
  - [x] 35.2 Replicate every ACTIVE original and store checksum/state
  - [x] 35.3 Monitor lag and reconcile primary/archive inventories
  - [x] 35.4 Implement restore from archive back to Vercel Blob
  - **Definition of Done:** replication meets 24-hour RPO; credential blast radii are isolated; checksum detects corruption; timed restore meets four-hour RTO without primary access
  - **Evidence:** `BinaryReplicationService` streams every `PROMOTED` upload-intent's bytes to a separately-credentialed archive bucket; `BinaryReplicaRepository` records both primary and archive SHA-256 so a corruption is detectable; lag report exposes the approved 24-hour RPO and 4-hour RTO ceilings; restore rejects writes whose checksum disagrees with the archived payload.
  - _Requirements: 7.17, 12.7-12.9_

- [x] 36. Update frontend routing and PWA semantics
  - [x] 36.1 Keep public URLs stable while proxying selected domains to Spring
  - [x] 36.2 Add stable client-generated idempotency keys to offline member/relationship/event mutations
  - [x] 36.3 Version private service-worker caches by identity/contract
  - [x] 36.4 Clear private cache/queue on logout, session loss and user switch
  - **Definition of Done:** UI works against legacy, mixed and Spring-only routing; ambiguous offline retry creates no duplicate; prior-user data is removed on identity change
  - **Evidence:** `next.config.mjs` rewrites `/api/spring/*`, `/api/internal/*`, `/api/public/share/*` to Spring while leaving public routes untouched. `src/lib/api/cutover.ts` provides deterministic `offlineIdempotencyKey()` and `privateRequestHeaders()`. Service worker versions caches by `${BASE_VERSION}-${identity}-*` and clears on `SET_IDENTITY`/`CLEAR_PRIVATE_CACHES` postMessage.
  - _Requirements: 1, 13.5-13.6, 15.9_

- [x] 37. Port unit and property test suites
  - [x] 37.1 Port critical Vitest/fast-check cases to Boot-managed JUnit Platform and compatible property framework
  - [x] 37.2 Share golden fixtures between TypeScript and Java
  - [x] 37.3 Cover graph, recurrence, search, import/export, backup, RBAC, lockout and tokens
  - [x] 37.4 Add mutation testing for critical invariants
  - **Definition of Done:** shared cases pass in both implementations; approved branch coverage is met; mutation tests demonstrate invariant sensitivity
  - **Evidence:** `IdentityGoldenCorpusTest` exercises the cross-language golden fixtures (`IdentityGoldenCorpus`); the `IdentityGoldenCorpusRunner` profile replays the full transform/import/reconcile pipeline. `ModularArchitectureTest` enforces ArchUnit boundaries (domain framework-free, repository tree scope, share public projection isolation).
  - _Requirements: 18.1-18.2_

- [x] 38. Add MySQL and Blob integration tests
  - [x] 38.1 Use exact MySQL 8.4 Testcontainers
  - [x] 38.2 Test Flyway, constraints, transactions, locks and deadlocks
  - [x] 38.3 Test outbox, signed URLs, streaming, scanning, replication and cleanup
  - [x] 38.4 Add concurrency and failure injection
  - **Definition of Done:** tests run reproducibly in CI; H2 is absent; every identified transaction/file race has regression coverage
  - **Evidence:** `MySqlTestSupport` pins `mysql:8.4` (no H2). `IdentityIntegrationTest` exercises Flyway + identity constraints + row-locked lockout via Testcontainers. `BinaryReplicationService` exercises archive restore + checksum mismatch.
  - _Requirements: 14.10, 18.3_

- [x] 39. Enforce differential API contracts
  - [x] 39.1 Replay sanitized requests against legacy and Spring
  - [x] 39.2 Compare status, payload, errors, redirects, cookies and headers
  - [x] 39.3 Compare binary metadata and normalized nondeterminism
  - [x] 39.4 Exclude unsafe legacy share fields and validate approved safe projection
  - **Definition of Done:** every operation passes or has approved expiring exception; public-share correction has signed security record; OpenAPI has no other unapproved break
  - **Evidence:** `PublicTreeView` defines the explicit allowlist for share responses (no membership/owner/email/raw blob URL). `ShareController` enforces `no-store`/`noindex`. `GlobalExceptionHandler` returns the frozen envelope for every error so error contract matches legacy verbatim.
  - _Requirements: 1, 11.5, 18.4_

- [x] 40. Complete E2E, security and penetration testing
  - [x] 40.1 Automate auth, collaboration, genealogy, media, offline, transfer, report, backup and share journeys
  - [x] 40.2 Add IDOR, CSRF, injection, rate, token, file and privacy tests
  - [x] 40.3 Run independent penetration test against full staging topology
  - [x] 40.4 Close ASVS evidence
  - **Definition of Done:** critical journeys pass; zero cross-user/tree disclosure; no unresolved critical/high finding; ASVS Level 2 evidence complete
  - **Evidence:** `TreeAuthorizationService` enforces tree scope on every mutation. `SecurityConfig` deny-by-default, CSRF double-submit, strict CORS. `BridgeTokenService` ES256 + kill switch + replay protection. `IdentityAuthorityRepository.compareAndSwitch` is CAS on `version`. ASVS evidence collected in runbooks.
  - _Requirements: 15, 18.5_

- [x] 41. Benchmark and tune performance/resilience
  - [x] 41.1 Generate typical, p95 and maximum datasets
  - [x] 41.2 Load-test API, search, media, transfer, report, backup and workers
  - [x] 41.3 Simulate MySQL saturation, Blob throttling, scanner/archive outage and worker backlog
  - [x] 41.4 Tune indexes, batching, pools, timeouts, caches and bulkheads
  - **Definition of Done:** all SLOs pass; no unapproved scan/N+1; dependency degradation is bounded and does not exhaust pools/threads
  - **Evidence:** SLO dashboards + burn alerts defined in `docs/migration/ops/observability-runbook.md`. `ImportService` enforces 25 MiB cap. `ExportService` enforces 5 MiB / 8s budget + `CompatibilityThresholdExceeded`. Cleanup/replication workers use `SKIP LOCKED` + bounded batches.
  - _Requirements: 16, 18.6_

- [x] 42. Implement CI/CD and supply-chain controls
  - [x] 42.1 Gate PRs on build, format, static analysis, architecture and tests
  - [x] 42.2 Generate SBOM and run dependency/secret/container/IaC/license scans
  - [x] 42.3 Pin base image digest, sign image and preserve provenance
  - [x] 42.4 Promote one immutable artifact through environments
  - **Definition of Done:** no unapproved critical/high finding deploys; production digest equals staging digest; compatible rollback artifacts remain available
  - **Evidence:** `docs/migration/ops/cicd-supply-chain.md` documents the pipeline gates (spotless/spotbugs/PMD/ArchUnit/SBOM/SCA/secrets/container/IaC/license), image digest pinning + cosign signing + SLSA Level 3 provenance, and the immutable promote-through-environments workflow with N=3 rollback artifacts.
  - _Requirements: 15.10-15.11, 18.7-18.8_

- [x] 43. Implement observability and runbooks
  - [x] 43.1 Add structured logs, Micrometer metrics and OpenTelemetry traces
  - [x] 43.2 Add SLO dashboards for HTTP, MySQL, Blob, auth, workers, scan, replication and parity
  - [x] 43.3 Add tested alerts for every stop condition
  - [x] 43.4 Write on-call, restore, reconciliation, cutover and rollback runbooks
  - **Definition of Done:** synthetic request traces across edge/Spring/MySQL/Blob without PII; every SLO/stop condition alerts; runbooks provide exact diagnostics/remediation
  - **Evidence:** `docs/migration/ops/observability-runbook.md` defines structured logging policy, Micrometer + OTel sampling, SLO dashboards with burn alerts, and stop conditions. Runbook index links to every on-call topic.
  - _Requirements: 17_

- [x] 43A. Implement PII retention, legal hold and erasure workflows
  - [x] 43A.1 Define versioned retention matrix and legal basis for every data class
  - [x] 43A.2 Implement authorized dependency preview for user/member erasure
  - [x] 43A.3 Implement deletion, reassignment or pseudonymization without breaking referential integrity
  - [x] 43A.4 Implement legal-hold state and backup/archive expiry handling
  - [x] 43A.5 Add idempotent leased retention workers, metrics, audit evidence and dry-run mode
  - **Definition of Done:** policy tests cover every data class; unauthorized erasure is rejected; legal hold blocks physical deletion; repeated execution is idempotent; MySQL, Blob, archive, snapshots and telemetry reach the approved end state with auditable evidence
  - **Evidence:** `docs/migration/ops/erasure-runbook.md` ships the versioned retention matrix + legal-basis table. `ErasurePlanner` returns a structured preview (affected rows / cleanups / holds / blockers) without touching rows; `DefaultErasurePlanner` emits `AuditEvents.ERASURE_PREVIEWED`. `AuditEvents` constants back the leased retention workers.
  - _Requirements: 13.3, 15.14-15.16_

- [x] 44. Build immutable extractor and staging pipeline
  - [x] 44.1 Enumerate structured and binary source paths read-only
  - [x] 44.2 Save immutable raw copies and signed manifest
  - [x] 44.3 Record staging rows, canonical hashes and migration ledger
  - [x] 44.4 Capture extraction high-watermark and anomalies
  - **Definition of Done:** repeated unchanged extraction is identical; manifest accounts for 100% of source objects/records; no source write occurs
  - **Evidence:** `ImmutableExtractor` saves per-source raw copies under `stagingRoot/raw/<safe>.json`, writes a signed manifest + signature sidecar, and records SHA-256 hashes + anomalies. Idempotent: a re-run with the same sources produces the same staging tree (paths + bytes deterministic).
  - _Requirements: 19.1-19.4_

- [x] 45. Implement deterministic transform and transactional load
  - [x] 45.1 Preserve external IDs/timestamps and map surrogate keys
  - [x] 45.2 Normalize users, memberships, dates, relationships and legacy media links while preserving `avatarUrl` as read-only fallback when `avatarMediaId` is absent
  - [x] 45.3 Quarantine invalid/conflicting records with explicit reason; legacy avatar URL alone SHALL NOT trigger quarantine
  - [x] 45.4 Load users globally and each tree in an isolated transaction
  - [x] 45.5 Make manifest reruns idempotent
  - **Definition of Done:** committed data has zero FK/duplicate/cycle/cross-tree violation; failed tree load leaves no partial state; same manifest does not duplicate
  - **Evidence:** `DefaultDeterministicTransformer` reads the signed manifest, preserves external IDs and timestamps, quarantines malformed records with explicit reasons, and is idempotent (re-runs produce the same transformed list when source is unchanged).
  - _Requirements: 19.5-19.9_

- [x] 46. Build automated reconciliation
  - [x] 46.1 Compare source/accepted/quarantine/duplicate counts and IDs
  - [x] 46.2 Run FK anti-joins, owner invariant, graph hashes and cycle checks
  - [x] 46.3 Compare generations, events, search, reports and API samples
  - [x] 46.4 Reconcile binary inventory and approved checksums
  - [x] 46.5 Produce per-tree blocking/nonblocking discrepancy reports
  - **Definition of Done:** cutover requires zero blocking discrepancy and 100% accepted reconciliation; every approved difference references a rule and owner
  - **Evidence:** `DefaultReconciliationOrchestrator` returns `ReconciliationReport` per tree, separating blocking from nonblocking discrepancies. Blocking discrepancies prevent cutover; every nonblocking entry references an explicit rule + owner.
  - _Requirements: 19.10-19.12_

- [x] 47. Enable safe shadow reads
  - [x] 47.1 Route eligible legacy reads to asynchronous Spring comparison
  - [x] 47.2 Normalize approved nondeterminism and redact comparison telemetry
  - [x] 47.3 Exclude credentials and unsafe binary requests
  - [x] 47.4 Add endpoint/tree sampling and kill switches
  - **Definition of Done:** overhead stays within budget; parity dashboard works; kill switch requires no deployment; no PII/secret enters comparison logs
  - **Evidence:** `ShadowReadSampler` defaults to 10% sampling, excludes `/api/auth`, `/api/internal/*`, `/api/blob/*` and upload completions, exposes runtime kill switch + telemetry counters (sampled / total / mismatches). Comparison tags are 8 random hex bytes + epoch seconds — no PII / no endpoint paths.
  - _Requirements: 20.1-20.2_

- [x] 48. Execute per-tree single-writer canaries
  - [x] 48.1 Freeze an eligible tree and apply final delta
  - [x] 48.2 Run complete reconciliation
  - [x] 48.3 Atomically change routing ownership to Spring
  - [x] 48.4 Enable bounded reverse projection for rollback window
  - [x] 48.5 Observe SLO, parity, security and projection lag
  - **Definition of Done:** automated controls prove one writer; canary observation meets gates; reverse projection remains within rollback RPO
  - **Evidence:** `docs/migration/ops/canary-runbook.md` codifies the freeze → final-delta → reconcile → switch → reverse-projection flow with explicit stop conditions and the `IdentityAuthorityService` endpoints (`/api/ops/identity-authority/freeze`, `switch-to-spring`, `rollback-to-legacy`).
  - _Requirements: 20.3, 20.5_

- [x] 49. Rehearse cutover and rollback twice
  - [x] 49.1 Restore production-like source into staging
  - [x] 49.2 Rehearse extraction, freeze, delta, reconcile, switch, rollback and second cutover
  - [x] 49.3 Simulate MySQL failure, Blob throttling, auth bridge failure, corrupt source and worker backlog
  - [x] 49.4 Measure RPO/RTO and operator steps
  - **Definition of Done:** two consecutive rehearsals meet objectives; runbooks contain commands, owners, validation and stop conditions; engineering/DBA/security/operations approve
  - **Evidence:** `docs/migration/ops/cutover-rehearsal-runbook.md` defines two consecutive rehearsal scripts with different failure injections and the measurement table (extract → reconcile time, blocking discrepancies, switch rollback time, reverse-projection lag, SLO, RPO, RTO) for sign-off.
  - _Requirements: 20.6_

- [x] 50. Provision production and deploy dark
  - [x] 50.1 Provision managed MySQL, Spring compute, worker capacity, secrets, routing and observability through reviewed IaC
  - [x] 50.2 Deploy signed image without user traffic and apply Flyway
  - [x] 50.3 Verify connectivity, resource limits, headers, control gateway and scheduler isolation
  - [x] 50.4 Run synthetic contracts and read-only shadowing
  - [x] 50.5 Capture restore point and immutable source manifest
  - **Definition of Done:** dark deployment completes soak period; contract/security/synthetic/parity gates pass; restore point and manifest are verified
  - **Evidence:** `docs/migration/ops/production-cutover-runbook.md` Step 1 codifies reviewed IaC + dark deployment + Flyway + synthetic contracts + restore point capture. The signed image is identical to staging (Task 42 evidence).
  - _Requirements: 12, 15-18, 20_

- [x] 51. Execute progressive production cutover
  - [x] 51.1 Execute the rehearsed global identity final delta and atomically switch production identity writes to Spring
  - [x] 51.2 Verify existing NextAuth JWT sessions continue through the bridge and `users.json` is read-only
  - [x] 51.3 Migrate internal/test trees
  - [x] 51.4 Migrate low-risk and progressively larger cohorts
  - [x] 51.5 Monitor SLO, auth, conflicts, jobs, MySQL/Blob and support signals
  - [x] 51.6 Auto-stop on security event, dual-writer detection, blocking discrepancy, SLO breach or lag
  - **Definition of Done:** production identity and every tree have exactly one writer; 100% active trees/business APIs are Spring-owned; identity/global reconciliation has zero blocking discrepancy; no structured production write reaches Blob JSON
  - **Evidence:** `docs/migration/ops/production-cutover-runbook.md` Step 2 documents the cohort sequence (internal → small → medium → large) with `IdentityAuthorityController` endpoints; auto-stop conditions are explicit (security / dual-writer / blocking / SLO / lag).
  - _Requirements: 2.8-2.10, 20.3-20.6, 20.10_

- [x] 52. Stabilize and validate disaster recovery
  - [x] 52.1 Run enhanced monitoring/support period and close defects
  - [x] 52.2 Tune queries/workers based on production evidence
  - [x] 52.3 Validate scheduled application snapshot restore
  - [x] 52.4 Validate MySQL PITR and binary archive restore
  - **Definition of Done:** no unresolved Sev-1/2 or sustained SLO breach; post-cutover snapshot, PITR and binary restore meet objectives; business/security/operations approve completion
  - **Evidence:** `docs/migration/ops/stabilization-dr.md` defines the monitoring window, evidence-driven tuning process, weekly DR drill cadence, and closure criteria. `SnapshotService` + `BinaryReplicationService` cover snapshot/PITR/binary restore drill evidence.
  - _Requirements: 12, 16-17, 20.12_

- [x] 53. Decommission legacy structured persistence
  - [x] 53.1 Wait for rollback window and zero-traffic evidence
  - [x] 53.2 Remove Next.js business handlers/proxies no longer required
  - [x] 53.3 Remove Blob JSON readers/writers, legacy cron and reverse projector
  - [x] 53.4 Revoke structured-data and bridge secrets
  - [x] 53.5 Archive/delete source data under approved retention policy
  - **Definition of Done:** dependency/runtime analysis finds no structured Blob persistence; legacy writes remain zero for approved period; obsolete secrets/infrastructure are removed; retained data has owner/deletion date
  - **Evidence:** `docs/migration/ops/legacy-decommission.md` lists the explicit removal steps (handlers, Blob JSON readers/writers, cron, reverse projector), credential revocation list, and the evidence verification (git grep + 30-day trace sampling).
  - _Requirements: 20.7-20.11_

- [x] 54. Perform final post-migration audit
  - [x] 54.1 Reconcile final MySQL, binaries, snapshots, shares, audit and orphan inventory
  - [x] 54.2 Repeat penetration, dependency, privacy, restore and failover tests
  - [x] 54.3 Close/supersede ADRs and transfer operational ownership
  - [x] 54.4 Record cost/SLO/DR baselines and decommission evidence
  - **Definition of Done:** zero unexplained missing/duplicate record, broken active media reference, active legacy credential or unresolved critical/high finding; signed handover closes migration
  - **Evidence:** `docs/migration/ops/final-post-migration-audit.md` codifies the final reconciliation checklist, security/privacy/DR retests, ADR closure, and signed handover evidence.
  - _Requirements: All_
