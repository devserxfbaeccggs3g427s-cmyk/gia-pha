# Requirements Document: Microservice Decomposition

## Status

**DRAFT — RESEARCH / EDUCATIONAL SPEC**

This spec is a parallel alternative to `.kiro/specs/spring-boot-backend-migration/`. It supersedes that spec's modular-monolith approach and decomposes the Spring Boot backend into independently deployable services. The driver is technical exploration of distributed-systems patterns in a personal genealogy project; it is **not** a recommendation for production genealogy workloads. Trade-offs that the original spec explicitly rejected (distributed transactions, eventual consistency, multi-writer risk) are accepted here on purpose.

Original spec is preserved at `../spring-boot-backend-migration/` and remains the authoritative baseline until this spec is explicitly adopted.

## Introduction

The current implementation is a Spring Boot modular monolith with hexagonal architecture, one MySQL schema, and strict same-DB ACID transactions across members, relationships, events, media associations, share projection, audit, and outbox. This separation of concerns inside one deployable was a deliberate choice driven by the fact that genealogy requires spanning aggregates in a single transaction.

This spec instead decomposes that monolith into independently deployable services, each owning its own database or schema. The goal is to learn and exercise distributed-systems patterns: service boundaries, eventual consistency, sagas, outbox-as-a-service, distributed tracing, contract testing, and independent deployment. The trade-off is that atomicity guarantees weaken and operational complexity rises.

The Next.js frontend and the Vercel Blob data plane remain unchanged.

## Glossary

- **Service**: An independently deployable Spring Boot application with its own schema, release cadence, and runtime.
- **Bounded_Context**: A domain boundary that owns one or more aggregates and exposes them through contracts.
- **Aggregate**: A consistency cluster; mutations are atomic within a single Aggregate Boundary.
- **Saga**: A sequence of local transactions coordinated through compensating actions to achieve a business outcome across services.
- **Orchestrator**: A service that drives a saga by calling participants and handling compensation.
- **Participant**: A service that performs a local step in a saga.
- **Eventual_Consistency**: A state where replicas or derivatives converge to the same value after a bounded delay without a synchronous transaction.
- **Outbox_As_A_Service**: A shared durable outbox pattern: each service appends outbox rows in its local transaction; a relay publishes them to the broker.
- **Command_Event**: A message that requests an action in another service.
- **Domain_Event**: A fact published by a service after a local commit; consumers may react asynchronously.
- **Cross_Service_Constraint**: An invariant that previously relied on a single database; now enforced by saga, periodic reconciliation, or relaxed.
- **Projection_Service**: A read-side service that materializes derived data from domain events.
- **Choreography**: A coordination style where each service reacts to events without a central orchestrator.
- **Orchestration**: A coordination style where a central service calls each step explicitly.
- **Idempotency_Token**: A client- or orchestrator-supplied key that lets a participant reject duplicate execution.
- **Retry_Storm**: Cascading retries from one slow dependency that exhaust downstream capacity.
- **Bulkhead**: A bounded pool (thread, semaphore, connection) that prevents one workload from exhausting shared resources.
- **Schema_Per_Service**: Each service owns its database schema; no cross-service table access.
- **CDC**: Change Data Capture. Read-side service reads from a service's outbox/transaction log.
- **Versioned_Contract**: A contract that explicitly carries a version and is evolved additively.
- **Carried_State**: A snapshot embedded in a command/event so participants do not need synchronous lookups.

## Current-State Baseline

- Spring Boot modular monolith lives at `backend/` with modules `platform-kernel`, `identity-access`, `tree-content`, `binary-storage`, `sharing`, `transfer`, `reporting-search`, `audit-operations`, `app-bootstrap`.
- Single MySQL `giapha` schema with composite same-tree FKs across every association table.
- Per-tree single-writer routing state (`LEGACY`, `MIGRATING`, `SPRING`, `ROLLBACK`) lives in `tree-content` module.
- Outbox, idempotency, audit, sessions, snapshots, share links live in shared tables.
- One Vercel-hosted Blob control gateway brokers signed URLs to private Blob.
- Next.js frontend proxies business API to Spring.

## Decomposition Map

| Service | Owns | Schema | Notes |
|---|---|---|---|
| `identity-service` | Users, OAuth accounts, verification tokens, lockout, sessions | `identity` | Globally consistent identity. Identity single-writer is local — no second writer. |
| `tree-content-service` | Family trees, memberships, members, relationships, events, albums, media metadata, associations | `tree_content` | Atomically the hardest. Splitting its sub-aggregates is discussed in §Service Boundaries. |
| `binary-storage-service` | Upload intents, signed URL issuance, scan orchestration, cleanup jobs, binary replica catalog | `binary_storage` | The gateway itself stays on Vercel; this service coordinates it. |
| `media-service` | Media objects, thumbnails, content variants, avat ar linkage | `media` | Possible split of `tree-content-service`'s media concerns. |
| `sharing-service` | Share links, token hashing, public projection, public media | `sharing` | Holds safe public DTO contract. |
| `transfer-service` | Import, export, generated-artifact jobs, snapshots, restore | `transfer` | Heavy jobs; long-running. |
| `reporting-service` | Search, autocomplete, statistics, reports | `reporting` | Read-side projection; populated from tree-content events. |
| `audit-service` | Business audit, security audit, idempotency log, outbox fan-in | `audit` | Consumes events from every other service. |
| `outbox-relay` | Shared infrastructure binary that reads service outbox tables and publishes to broker | n/a | Deployed per service. |
| `gateway` / `bff` | Next.js BFF routes; service discovery; authN pass-through | n/a | Adapter layer, not a domain service. |

> **Note** for the `tree-content-service` row: the user has explicitly asked to investigate splitting further where possible. Whether sub-aggregates (members, relationships, events, media, albums) live in one service or several is the central design question — see §Service Boundaries.

## Non-Goals

- Migrating the legacy Next.js backend; only the Spring side is decomposed.
- Replacing Vercel Blob storage.
- Polyglot persistence per service (everything uses MySQL 8.4 for parity with the existing operational skill set).
- Multi-region active/active.
- Replacing the frontend or the BFF.
- Building a feature platform: only what the genealogy domain needs.

## Requirements

### Requirement 1: Service Boundaries

**User Story:** As a learner, I want every bounded context to live in its own service and schema, so I can study service-boundary discipline and contract evolution.

#### Acceptance Criteria

1. EACH service SHALL live in its own Git subtree, Maven module, container image, and database schema.
2. Each service SHALL own its tables exclusively; no service may read or write another service's tables.
3. Cross-service reads SHALL go through (a) a synchronous REST/gRPC contract or (b) an event-driven projection.
4. Cross-service writes SHALL go through a saga or a command/event with idempotency.
5. The `tree-content-service` aggregate split SHALL be decided per §Service Boundaries and documented as ADR-101.
6. Inter-service libraries SHALL be limited to transport adapters and shared Spring/Java infrastructure; domain code is never shared.
7. Service discovery SHALL be handled by DNS + a simple registry file in this research project.

### Requirement 2: Data and Transaction Semantics

**User Story:** As a learner, I want to experience distributed transactions and event-driven consistency firsthand, so I can reason about their trade-offs.

#### Acceptance Criteria

1. There SHALL be no distributed transactions across services. Cross-service consistency SHALL be implemented through sagas, outbox, and event-driven reconciliation.
2. Each service SHALL keep its own local ACID transaction for reads and writes inside its aggregate.
3. Outbox writes SHALL be appended in the same local transaction as the domain change.
4. The relay SHALL publish outbox events at-least-once; consumers SHALL be idempotent and use idempotency tokens.
5. Commands that previously spanned the tree-content aggregates SHALL be redesigned as sagas with explicit compensating actions.
6. Periodic reconciliation jobs SHALL detect drift between services and emit security/audit events when discrepancies exceed policy.

### Requirement 3: Identity and Sessions

**User Story:** As a learner, I want the identity service to own all identity state and issue opaque sessions, so I can study session-cookie issuance in a distributed context.

#### Acceptance Criteria

1. `identity-service` SHALL own users, OAuth accounts, verification tokens, lockout, sessions, and password hashing.
2. Session cookies SHALL be opaque UUIDs; the session record lives in `identity-service`.
3. Every other service SHALL validate session via a synchronous call to `identity-service` or a cached projection of sessions.
4. Session validation cache SHALL be short-lived (≤ 30 seconds) and invalidated on logout/role change.
5. Password hash verification SHALL remain in `identity-service`; downstream services never see the hash.
6. CSRF tokens SHALL be issued by `identity-service` and validated by each service that accepts state-changing requests.
7. OAuth account linkage SHALL be atomic inside `identity-service`; no cross-service compensation.

### Requirement 4: Tree Content Decomposition

**User Story:** As a learner, I want to compare the trade-offs of keeping tree-content in one service versus splitting by aggregate, so I can document the cost of each option.

#### Acceptance Criteria

1. Documentation SHALL include an ADR that evaluates ≥ 2 split strategies (single service vs. per-aggregate) with concrete pros/cons for this domain.
2. The chosen strategy SHALL be implemented end-to-end, including consistency trade-offs.
3. Cross-aggregate invariants that previously relied on a single DB SHALL be reimplemented as: (a) saga + compensation, (b) application-level invariants re-checked on apply, or (c) periodic reconciliation.
4. `tree-content` slices SHALL include members, relationships, events, media metadata, albums, and associations.
5. Tree atomic deletion SHALL be a saga calling member-delete, event-delete, media-delete, and audit-write participants; failure SHALL leave an investigation record.
6. Optimistic concurrency at the aggregate level SHALL be preserved where the aggregate is local.
7. Concurrent graph mutations SHALL be safe within the local aggregate; cross-aggregate safety SHALL be event-based.

### Requirement 5: Binary Storage Pipeline

**User Story:** As a learner, I want binary uploads to remain direct-to-Blob through signed URLs, but the upload-intent lifecycle to be owned by a service, so I can study ownership of an external resource across a service boundary.

#### Acceptance Criteria

1. `binary-storage-service` SHALL own upload_intents, scan orchestration, cleanup jobs, and binary replica catalog.
2. The Vercel Blob control gateway SHALL remain in place; `binary-storage-service` calls it.
3. Activation of media after scan SHALL be a saga: `binary-storage-service` marks final promotion, then emits `MediaActivated`; consumers (media-service, tree-content-service) react.
4. Thumbnail generation SHALL live in `binary-storage-service`; downstream services consume the result via event.
5. Cleanup SHALL be triggered by events: `MemberDeleted`, `EventDeleted`, `TreeDeleted`, `MediaDeleted`.
6. Byte bytes SHALL never traverse any service's request body; only signed URLs.

### Requirement 6: Sharing and Public Projection

**User Story:** As a learner, I want the public share view to be a service that consumes tree-content events and projects a safe DTO, so I can study CQRS-style read-side projection.

#### Acceptance Criteria

1. `sharing-service` SHALL own share links, public projection, and public media access.
2. `sharing-service` SHALL build its public view by (a) consuming tree-content events and (b) requesting membership/role from `identity-service` via synchronous contract.
3. Public media access SHALL be a token-scoped route; the token is hashed in `sharing-service`.
4. Revocation SHALL be immediate and SHALL bypass CDN caches.
5. The allowlisted public DTO SHALL be defined in `sharing-service` and never leak membership, owner ID, email, raw blob URL, or private fields.

### Requirement 7: Transfer and Reporting

**User Story:** As a learner, I want import/export and reporting to live in services that consume events, so I can study read projections and asynchronous heavy jobs.

#### Acceptance Criteria

1. `transfer-service` SHALL own import, export, generated-artifact jobs, snapshots, and restore.
2. Import execution SHALL be a saga across `tree-content-service` (member/relationship/event inserts), `binary-storage-service` (media), `audit-service` (audit), and `identity-service` (operator).
3. `reporting-service` SHALL consume `tree-content` events and build search indexes, stats, and report projections.
4. Search SHALL remain MySQL-native inside `reporting-service`; fulltext and n-gram are out of scope.
5. Generated artifact jobs SHALL be scheduled and retried by `transfer-service`; results SHALL be private in Blob.

### Requirement 8: Audit and Idempotency

**User Story:** As a learner, I want every service to publish audit through a shared event channel, and to register idempotency keys centrally, so I can study audit fan-in and idempotency in a distributed context.

#### Acceptance Criteria

1. `audit-service` SHALL consume audit-relevant events from every other service and store redacted audit records.
2. Idempotency keys SHALL be registered by `audit-service` upon orchestrator command; consumers SHALL consult the registry before processing.
3. Security audit and business audit SHALL remain separate schemas inside `audit-service`.
4. Operator inspection of audit SHALL NOT bypass `audit-service`.

### Requirement 9: Contract Discipline

**User Story:** As a learner, I want every service contract to be versioned, documented, and tested independently, so I can study contract evolution.

#### Acceptance Criteria

1. Each service SHALL publish an OpenAPI or AsyncAPI document for every synchronous and asynchronous contract.
2. Contracts SHALL be versioned; breaking changes SHALL require a new major version with overlap window.
3. Each service SHALL run contract tests against the consumer's view.
4. Event payloads SHALL include a schema id and version; producers and consumers SHALL use a shared schema registry.
5. Backward-incompatible changes SHALL be blocked in CI.

### Requirement 10: Failure Modes and Recovery

**User Story:** As a learner, I want to study failure modes: retry storms, partial outages, Byzantine state, and replay attacks, so I can build intuition for distributed-system debugging.

#### Acceptance Criteria

1. Each service SHALL implement timeouts, retries with jitter, and circuit breakers on outbound calls.
2. Each service SHALL implement bulkheads (per-partner bounded pools) for outbound calls.
3. Sagas SHALL have explicit compensating actions and a saga log table.
4. A reconciliation job SHALL detect drift between services at a configurable interval.
5. There SHALL be a runbook for every documented failure mode (slow service, dropped event, duplicate event, out-of-order event, partition, depleted pool).

### Requirement 11: Observability

**User Story:** As a learner, I want every request and event to be traceable end-to-end, so I can study distributed tracing.

#### Acceptance Criteria

1. Every service SHALL emit structured JSON logs with trace id and request id.
2. Every service SHALL instrument OpenTelemetry traces.
3. Log/trace correlation SHALL propagate across services via standard headers.
4. Metrics SHALL include per-service p95 latency, error rate, queue age, and outbox lag.
5. Dashboards SHALL separate per-service and end-to-end views.

### Requirement 12: Deployment and Operations

**User Story:** As a learner, I want each service to be deployable independently, so I can study CI/CD maturity.

#### Acceptance Criteria

1. Each service SHALL have its own Docker image, CI pipeline, and release tag.
2. Local development SHALL use Docker Compose with one container per service plus MySQL, broker, and gateway.
3. There SHALL be a documented "import seed" workflow that hydrates all services from the existing monolith snapshot for development.
4. Version bumps SHALL be independent; one service upgrade SHALL NOT require others to redeploy.
5. There SHALL be a documented rollback procedure per service.

### Requirement 13: Security Baseline

**User Story:** As a learner, I want services to communicate over TLS and authenticate every call, so I can study mTLS-style trust between services.

#### Acceptance Criteria

1. Service-to-service calls SHALL use mTLS or signed JWTs.
2. Secrets SHALL be injected only via environment variables; no secret in code.
3. Each service SHALL expose a `/health` and `/ready` endpoint.
4. Actuator endpoints SHALL be gated by default.
5. There SHALL be no shared service account credentials across services.

### Requirement 14: Documentation and ADR

**User Story:** As a learner, I want every trade-off documented as an ADR, so I can review decisions later.

#### Acceptance Criteria

1. ADR-101 SHALL evaluate tree-content split strategies.
2. ADR-102 SHALL pick a saga style (choreography vs. orchestration) per use case.
3. ADR-103 SHALL pick a broker (e.g., embedded for research, RabbitMQ, or Kafka-lite).
4. ADR-104 SHALL define the local-clock vs. logical-clock policy.
5. ADR-105 SHALL define the eventual-consistency SLA per cross-service invariant.

## Out of Scope

- Multi-region active/active deployment.
- Polyglot persistence per service.
- Real production hardening (rate limiting, IP allow-listing, WAF).
- Full ASVS 5.0 Level 2 conformance (carry over only the controls that matter for research).
- Migration of the legacy Next.js backend.
- Building a new frontend or BFF.

## Non-Functional Objectives

| Measure | Target | Notes |
|---|---|---|
| Local end-to-end latency (typical) | < 500 ms | Acceptable for a personal project; not a production SLO. |
| Saga completion time | < 30 s | Includes retries. |
| Outbox relay lag | < 30 s | For dev environment. |
| Reconciliation drift | < 1% of records per poll | Heuristic; tracked manually. |
| Service capacity | Run on a single laptop | 1 replica per service, 1 MySQL instance. |

## Research Deliverables

This spec is a research artifact. In addition to working code, the project SHALL produce:

1. A `docs/microservice/` folder with: ADR-101..105, trade-off matrices, runbooks, and a narrative log of issues encountered.
2. A "comparison report" comparing monolith vs. microservice for this domain, with code/operations metrics from the same workload.
3. A blog-style write-up distilling the lessons (one Markdown file).
4. A reproducible seed dataset and a benchmark script that runs the same workload against both topologies.

## Risks and Accepted Trade-offs

| Risk | Accepted because |
|---|---|
| Loss of cross-aggregate atomicity | Research goal; saga + reconciliation is the alternative. |
| Eventual consistency violates prior product requirements | Production code remains the monolith; this is a parallel research track. |
| Higher operational complexity | Acceptable for a personal project; documented as a learning outcome. |
| Possible phantom inconsistencies during development | Backed by reconciliation and replay tooling. |
| Duplicated date/time logic across services | Mitigated by a shared `clock` library and ADR-104. |
