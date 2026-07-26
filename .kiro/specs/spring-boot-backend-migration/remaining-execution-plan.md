# Remaining migration execution plan

This plan resumes at Task 17 and follows `.kiro/specs/spring-boot-backend-migration/tasks.md` in strict dependency order. A task is checked only after its implementation and every Definition of Done item are satisfied. Build and unit-test optimization are not the primary objective, but required task-specific proof, reconciliation, security, concurrency, contract, staging, and operational evidence remains part of each Definition of Done.

## Phase 1 — Identity and authorization

### Task 17 — Identity persistence
1. Audit and complete users, OAuth accounts, verification tokens, lockout fields, constraints, UTC/version semantics, and BCrypt compatibility.
2. Make legacy transformation deterministic for duplicate IDs, normalized email conflicts, provider collisions, and accepted/rejected accounting.
3. Extend reconciliation to users, OAuth links, verification state/tokens, lockout state, canonical counts, IDs, and hashes.
4. Implement constraint-safe duplicate handling and the required race/golden-corpus evidence.
5. Check Task 17 only after golden hashes authenticate and accepted identity data reconciles exactly.

### Task 18 — Transitional NextAuth bridge
1. Add the Next.js server-only exchange path that validates the existing NextAuth session.
2. Mint short-lived asymmetric bridge JWTs with pinned claims, algorithm, `kid`, authentication strength, and maximum five-minute lifetime.
3. Add Spring bridge authentication, issuer/audience/key/algorithm/expiry validation, replay protection, principal mapping, log redaction, overlapping-key rotation, and a runtime kill switch.
4. Ensure bridge tokens are used transiently and never enter browser persistent storage or logs.
5. Check Task 18 only after valid sessions work and invalid, expired, and replayed tokens fail.

### Task 19 — Final Spring authentication
1. Port registration validation and compatibility responses.
2. Implement verification-token issuance/consumption and durable email outbox delivery.
3. Implement credential login with dummy hashing, row-locked lockout accounting, legacy BCrypt acceptance, and rehash-on-login.
4. Implement constraint-safe Google/Facebook linkage.
5. Implement opaque server-side sessions, secure cookies, rotation, idle/absolute expiry, CSRF, logout, and revocation.
6. Check Task 19 only after all browser/authentication contracts and required security/failure scenarios are satisfied.

### Task 20 — Global identity cutover mechanics
1. Upgrade extraction/final-delta/reconciliation into an idempotent operational workflow.
2. Add a durable atomic identity-authority state machine, write freeze, optional MySQL-backed NextAuth compatibility adapter, and structural single-writer guards in both stacks.
3. Enforce read-only legacy `users.json` after authority transfer.
4. Implement compatible rollback projection and atomic routing reversal.
5. Add rehearsal automation/evidence for cutover and rollback without switching production identity.
6. Check Task 20 only after simulated states prove one writer and staging RPO/RTO reconciliation passes.

### Task 21 — Tree-scoped authorization
1. Centralize the owner/ADMIN/EDITOR/VIEWER permission matrix and pre-disclosure authorization.
2. Enforce immutable owner-admin, owner-only final deletion, safe public-share principals, and cache invalidation after membership changes.
3. Require tree scope in all repository/application paths and reject client-authoritative role/tree headers.
4. Check Task 21 after the complete role matrix and cross-tree/IDOR controls satisfy the DoD.

## Phase 2 — Tree content

### Tasks 22–28
1. **Task 22:** Implement tree and membership compatibility operations, atomic owner creation, revisioning, audit/outbox, role uniqueness, and durable deletion cleanup.
2. **Task 23:** Implement member CRUD/detail/preview, date/status/avatar invariants, transactional cascades, deferred binary cleanup, duplicate/merge internals, and V2 optimistic locking.
3. **Task 24:** Implement relationship APIs, canonical symmetric keys, graph locking, cycle prevention, and Java genealogy algorithms with no read-side migration writes.
4. **Task 25:** Implement event CRUD, normalized joins, recurrence including February 29, deterministic sorting, and same-tree transactional validation.
5. **Task 26:** Implement album/media APIs, upload-intent activation, fail-closed validation/scanning, authorized content variants, bounded WebP thumbnails, tombstones, and cleanup.
6. **Task 27:** Implement Vietnamese normalization (`đ/Đ → d`), indexed autocomplete/filtering, legacy score/order/`matchedFields`, profile scans, and add n-gram infrastructure only when measured thresholds require it.
7. **Task 28:** Import legacy change logs through allowlist/redaction/reclassification, implement immutable archive handling where required, and complete privileged runtime history/retention.
8. Check each task immediately after its own fixtures, integrity, concurrency, security, and reconciliation DoD is met.

## Phase 3 — Transfer, reporting, sharing, and recovery

### Tasks 29–35
1. **Task 29:** Port bounded GEDCOM/JSON/CSV preview and atomic append/replace execution with remapping, idempotency, and audit.
2. **Task 30:** Port synchronous JSON/GEDCOM/SVG/PNG/PDF/preview export with Vietnamese fonts, sanitization, streaming/resource limits, and compatibility thresholds.
3. **Task 31:** Define and implement the V2 generated-artifact job API, durable workers, ownership, normalized idempotency, private results, cancellation, expiry, audit, and cleanup.
4. **Task 32:** Implement whole-tree/branch statistics, deterministic demographic/timeline behavior, PDF output, artifact integration, and parity-safe SQL optimization.
5. **Task 33:** Implement legacy/new share-token handling, key rotation, hashed tokens, versioned safe public DTOs, token-scoped media, immediate revocation, and privacy headers.
6. **Task 34:** Implement complete versioned snapshots, manifests, safety snapshots, atomic restore, scheduling/retention, and PITR reconciliation.
7. **Task 35:** Implement independently credentialed encrypted binary archive replication, checksums, lag/inventory reconciliation, and timed restore to Vercel Blob.
8. Check each task only after its operation-specific parity, authorization, durability, and recovery DoD is met.

## Phase 4 — Frontend and verification

### Tasks 36–41
1. **Task 36:** Preserve public routes while switching domains, propagate stable offline idempotency keys, identity/contract-version private caches, and clear cache/queues on logout, session loss, or user switch.
2. **Task 37:** Port critical unit/property suites to JUnit/property testing, share golden fixtures, cover critical invariants, and add mutation testing.
3. **Task 38:** Add exact MySQL 8.4 and Blob integration coverage for Flyway, locks, transactions, deadlocks, outbox, scanning, replication, cleanup, concurrency, and failure injection.
4. **Task 39:** Enforce differential API contracts for responses, errors, cookies, redirects, headers, binaries, and the approved public-share correction.
5. **Task 40:** Complete critical E2E/security journeys, independent penetration testing, finding closure, and ASVS Level 2 evidence.
6. **Task 41:** Generate representative datasets, benchmark all paths, exercise dependency degradation, and tune indexes/pools/timeouts/caches/bulkheads from evidence.
7. Check each task after its specified coverage, parity, security, and SLO gates pass.

## Phase 5 — Delivery, operations, privacy, and migration pipeline

### Tasks 42–49
1. **Task 42:** Complete CI/CD gates, SBOM/scanning/license controls, digest-pinned and signed images, provenance, immutable promotion, and rollback artifact retention.
2. **Task 43:** Complete structured logs, metrics, traces, dashboards, stop-condition alerts, and executable on-call/restore/reconciliation/cutover/rollback runbooks.
3. **Task 43A:** Implement versioned retention/legal-basis policy, erasure dependency preview, safe deletion/reassignment/pseudonymization, legal holds, archive/backup expiry, and leased idempotent dry-run workers with evidence.
4. **Task 44:** Build read-only immutable extraction, raw copies, signed manifest, canonical hashes, migration ledger, high-watermark, and anomaly accounting.
5. **Task 45:** Build deterministic transformation and isolated transactional loading with preserved IDs/timestamps, quarantine reasons, avatar fallback, and manifest idempotency.
6. **Task 46:** Automate record/binary/FK/graph/algorithm/API reconciliation and produce per-tree blocking/nonblocking reports.
7. **Task 47:** Enable asynchronous sampled shadow reads with normalization, redaction, exclusions, bounded overhead, and runtime kill switches.
8. **Task 48:** Execute per-tree single-writer canaries with freeze, delta, reconciliation, atomic routing, bounded reverse projection, and gate observation.
9. **Task 49:** Run two complete production-like cutover/rollback rehearsals with failure scenarios, measured RPO/RTO, approved runbooks, and sign-off.
10. Check each task only when its immutable evidence and operational Definition of Done are complete.

## Phase 6 — Production deployment, stabilization, and closure

### Tasks 50–54
1. **Task 50:** Provision reviewed production IaC, deploy the signed artifact dark, apply Flyway, verify controls/scheduler isolation, run synthetic/shadow checks, and capture restore/manifests.
2. **Task 51:** Execute rehearsed identity switch and progressive tree cohorts with automatic stop conditions until identity and all active trees are Spring single-writer owned.
3. **Task 52:** Complete enhanced monitoring, evidence-driven tuning, application snapshot restore, MySQL PITR, binary restore, and business/security/operations approval.
4. **Task 53:** After the rollback window and zero-traffic evidence, remove legacy handlers/Blob JSON persistence/cron/reverse projection, revoke credentials, and archive/delete retained source under policy.
5. **Task 54:** Perform final relational/binary/snapshot/share/audit/orphan reconciliation, repeat security/privacy/DR tests, close ADRs, transfer ownership, and record signed handover evidence.
6. Check each task only after all production gates and final zero-defect/zero-legacy conditions are demonstrated.

## Tracking discipline

- Work on exactly one specification task at a time in the listed order.
- Before implementation, map every subtask and Definition of Done item to concrete code, configuration, migration, tooling, documentation, and evidence artifacts.
- After implementation, cross-reference `requirements.md` and `design.md`, close all gaps, then update the corresponding checkbox and subtasks in `tasks.md`.
- Never mark a task complete based only on scaffolding, schema presence, documentation, or unexecuted procedures.
- Preserve existing architecture: framework-free domain code, ports/adapters, mandatory tree scoping, Flyway-only DDL, durable outbox/cleanup, UTC timestamps, and secure secret handling.
