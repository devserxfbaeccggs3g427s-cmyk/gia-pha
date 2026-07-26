# Implementation Plan: Microservice Decomposition

This document is the task list for the parallel research spec `requirements.md` + `design.md` in this directory. Tasks are intentionally unchecked. Implement in dependency order. Each top-level task includes its scope and Definition of Done. Requirement references point to `requirements.md` and `design.md` in this directory.

A research deliverable accompanies each phase: a write-up in `docs/microservice/notes.md` capturing decisions, dead-ends, and lessons.

## Phase 0 — Foundation and tooling

- [ ] 0.1 Establish `infra/` Docker Compose with MySQL 8.4, RabbitMQ, Grafana, and a local CA
  - Build a one-shot `make up` that brings up all infrastructure.
  - **Definition of Done:** `docker compose up -d` succeeds; health checks pass; documented in `docs/microservice/runbooks/local-dev.md`.
- [ ] 0.2 Build `libs/contract-types` (generated DTOs/versioned events) and `libs/saga-framework`
  - Codify the Event format and Saga interface.
  - **Definition of Done:** libraries publish to a local Maven repo; contract tests pass.
- [ ] 0.3 Build `libs/outbox-relay` and `libs/clock`
  - One annotation `@TransactionalOutbox` that wraps the local insert + outbox row.
  - **Definition of Done:** unit tests prove at-least-once publish and idempotency.

## Phase 1 — Identity and shared contracts

- [ ] 1.1 Implement `identity-service`
  - Users, OAuth, verification, lockout, sessions, password hashing.
  - Service-to-service token issuance.
  - **Definition of Done:** registration, login, OAuth, logout, lockout, session validation pass; CSRF issued.
- [ ] 1.2 Implement `audit-service`
  - Redacted audit schemas; idempotency registry.
  - **Definition of Done:** audit events from identity-service are recorded; idempotency consultation works.
- [ ] 1.3 Implement API Gateway (Spring Cloud Gateway)
  - Session validation → user-context JWT (≤ 5 min) forwarded to services.
  - **Definition of Done:** BFF routes reach identity-service through the gateway; log shows correlated request id.

## Phase 2 — Tree and aggregates

- [ ] 2.1 Implement `tree-service`
  - Trees, memberships, tree authority state.
  - **Definition of Done:** create/list/detail/delete; owner-admin enforced; tree authority switch works.
- [ ] 2.2 Implement `members-service`
  - Member CRUD; local ACID; emits `MemberCreated` etc.
  - **Definition of Done:** list/create/detail/update/delete preview pass; saga compensations tested.
- [ ] 2.3 Implement `relationships-service`
  - Relationships + `relationships_graph` projection.
  - **Definition of Done:** canonical ordering, cycle detection, duplicate rejection work; concurrent cycle test fails as expected.
- [ ] 2.4 Implement `events-service`
  - Event CRUD + event_members + event_media.
  - **Definition of Done:** CRUD + recurrence + same-tree enforcement pass; February-29 handling correct.
- [ ] 2.5 Implement `media-metadata-service`
  - Media objects + associations + albums.
  - **Definition of Done:** CRUD pass; activation saga with `binary-storage-service` works.

## Phase 3 — Binary pipeline

- [ ] 3.1 Implement `binary-storage-service`
  - Upload intents, scan orchestration, cleanup, replicas.
  - **Definition of Done:** upload-to-Blob via signed URL works end-to-end; scan failure leaves nothing active; cleanup saga deletes bytes.

## Phase 4 — Sharing and reporting

- [ ] 4.1 Implement `sharing-service`
  - Share links, public projection, public media.
  - **Definition of Done:** event-driven projection rebuilds within 30 s; revocation is immediate; no PII leak.
- [ ] 4.2 Implement `reporting-service`
  - Search, autocomplete, stats, reports.
  - **Definition of Done:** Vietnamese search parity; reports deterministic; under bound.

## Phase 5 — Transfer

- [ ] 5.1 Implement `transfer-service`
  - Import, export, generated-artifact jobs, snapshots, restore.
  - **Definition of Done:** import orchestration saga handles compensation; export streaming under budget; snapshot round-trip exact.

## Phase 6 — Cross-cutting

- [ ] 6.1 Wire saga framework across services
  - Orchestrator + participants.
  - **Definition of Done:** every saga in Design §Sagas has a runbook entry and a passing test.
- [ ] 6.2 Reconciliation jobs per service
  - Drift detection + replay.
  - **Definition of Done:** at least one drift scenario is reproducible and remediated.
- [ ] 6.3 Observability
  - OpenTelemetry SDK, Grafana dashboards, alert rules.
  - **Definition of Done:** traces cross services; alerts trigger in inject-failure tests.
- [ ] 6.4 Security baseline
  - mTLS, signed outbox, gated actuator.
  - **Definition of Done:** documented in `docs/microservice/security.md`.

## Phase 7 — Research deliverables

- [ ] 7.1 Build workload benchmark script
  - Same workload against monolith and microservice topologies.
  - **Definition of Done:** `make bench` produces a JSON report and a CSV summary.
- [ ] 7.2 Comparison report
  - Quantitative and qualitative trade-off matrix.
  - **Definition of Done:** `docs/microservice/comparison.md` is reviewed.
- [ ] 7.3 Blog-style write-up
  - Distill the lessons.
  - **Definition of Done:** `docs/microservice/blog.md` is published locally.
- [ ] 7.4 Final notes
  - Document every dead-end, every divergence, every surprise.
  - **Definition of Done:** `docs/microservice/notes.md` is up-to-date.

## Tracking discipline

- Work on exactly one task at a time per phase.
- Update `requirements.md` if a task reveals a new requirement; document the change.
- Update `design.md` if a task reveals a divergence; document the rationale.
- Mark a task complete only when its Definition of Done is satisfied and the research write-up is updated.
- Preserve the original monolith spec; do not alter `../spring-boot-backend-migration/`.
