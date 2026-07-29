# Requirements: Spring Boot Microservices Migration

## Status

**APPROVED — PHASE 1 AUTHORITATIVE BASELINE**

This requirements-first specification supersedes the modular-monolith target. `design.md`, `tasks.md`, and the ADRs must trace to these requirements. Existing behavior remains useful migration input, but the approved breaking async contract and security corrections take precedence over legacy parity.

## Glossary

- **Gateway**: Edge/API Gateway plus temporary Next.js BFF retaining public `/api/**` routes.
- **Operation**: Durable cross-service mutation tracked by `operationId`.
- **Tree Revision/Epoch**: Authoritative version issued by Tree Access Service and applied by participant services.
- **Local Projection**: Service-owned read model derived from versioned Kafka events.
- **Blocking Discrepancy**: Unexplained mismatch that prevents cutover.
- **Source Service**: Service that owns a mutation and writes its audit record and outbox atomically.

## Requirements

### Requirement 1: Architecture and Ownership

**User Story:** As an engineering team, we need one unambiguous target architecture so services can evolve and operate independently.

#### Acceptance Criteria

1. The target SHALL be Spring Boot microservices deployed during migration without an intermediate modular monolith.
2. Identity, Tree Access, Member, Relationship, Event, Media & Album, Sharing, Search & Reporting, Transfer, Audit & Operations, and Migration & Reconciliation SHALL be independently owned bounded contexts.
3. Each service SHALL own a MySQL 8.4 database or isolated logical database, runtime credential, Flyway lifecycle, and domain model.
4. A service SHALL NOT read or write another service database, create cross-service foreign keys, share domain tables, or depend on a cross-service transaction.
5. Cross-service references SHALL use opaque external IDs and expected aggregate versions.
6. MySQL SHALL remain each domain's structured source of truth; Vercel Blob SHALL store only binary objects and generated artifacts.

### Requirement 2: API and Async Operation Contract

**User Story:** As a frontend user, I need durable and observable results for workflows that span services.

#### Acceptance Criteria

1. The Gateway SHALL retain public `/api/**` routing during strangler migration and publish versioned OpenAPI.
2. A mutation that starts cross-service work SHALL return `202 Accepted` with `{operationId,status:"PENDING",statusUrl}`.
3. The async contract SHALL be an approved breaking change and SHALL replace incompatible synchronous-success assumptions.
4. `GET /api/v2/operations/{operationId}` SHALL expose `PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`, `COMPENSATING`, `COMPENSATED`, or `MANUAL_REVIEW` without exposing secrets or unauthorized data.
5. Retriable commands SHALL accept idempotency keys; duplicate commands SHALL return the recorded operation/result and conflicting payload hashes SHALL return `409`.
6. Reads SHALL expose ETag/domain revision and, where projection freshness matters, a projection watermark.
7. Frontend contract tests SHALL prove polling behavior and absence of legacy synchronous-success assumptions.

### Requirement 3: Kafka Governance and Reliable Messaging

**User Story:** As an operator, I need reliable, governed event delivery for service coordination and projections.

#### Acceptance Criteria

1. A managed Kafka-compatible platform and Schema Registry SHALL be mandatory production infrastructure.
2. Domain events, Saga commands/replies, projection updates, cleanup, replication, and migration progress SHALL use Kafka.
3. Producers SHALL commit domain state, allowlisted audit evidence, and transactional outbox records in one local transaction.
4. Consumers SHALL use inbox deduplication, idempotent handlers, bounded retry with jitter, DLQ, replay, and gap reconciliation.
5. Topics and schemas SHALL be versioned by bounded context, backward compatible, and partitioned by `treeId`; identity events SHALL use `userId`.
6. Messages SHALL carry event, correlation, causation, operation, aggregate-version, timestamp, and trace metadata.
7. Event payloads SHALL exclude secrets, raw Blob URLs, and non-allowlisted PII; exactly-once end-to-end delivery SHALL NOT be claimed.

### Requirement 4: Saga and Consistency

**User Story:** As a user, I need cross-service workflows to converge safely and remain visible when participants fail.

#### Acceptance Criteria

1. Cross-service workflows SHALL use an orchestrated Saga owned by the service responsible for the use case.
2. Every participant SHALL atomically persist local state and outbox before acknowledging a command.
3. Delete-member, delete-tree, media activation, identity/tree cutover, import, and restore SHALL define transitions, timeouts, compensation, irreversible boundaries, and manual-review handling.
4. Reads SHALL hide tombstoned or pending-deletion entities while projections converge.
5. An operation SHALL become `SUCCEEDED` only after required participants reach its target revision/epoch.
6. Service outage, duplicate, reorder, broker outage, and compensation behavior SHALL be verified with fault injection.

### Requirement 5: Identity and Authentication

**User Story:** As an existing user, I need authentication migration without account reset or dual writers.

#### Acceptance Criteria

1. Identity Service SHALL own users, credentials, OAuth accounts, verification, lockout, opaque sessions, and revocation.
2. Existing BCrypt hashes, Google/Facebook linkage, registration limits, 24-hour verification, five-failure/15-minute lockout, and 30-minute idle session constraints SHALL be preserved.
3. During transition, Next.js SHALL validate NextAuth server-side and issue an asymmetric, audience-bound internal token lasting at most five minutes; browsers SHALL NOT persist it.
4. Identity cutover SHALL freeze writes, apply final delta, reconcile normalized emails/provider keys/security state, compare-and-set route authority, and preserve exactly one writer.
5. Final cookie sessions SHALL be HttpOnly, Secure, SameSite=Lax, rotated, revocable, expiration-bound, and CSRF protected.

### Requirement 6: Tree Access and Authorization Projections

**User Story:** As a collaborator, I need permissions to remain safe even when distributed projections lag.

#### Acceptance Criteria

1. Tree Access Service SHALL own trees, ownership, memberships, roles, lifecycle, and authoritative tree revision/epoch.
2. The owner SHALL always have effective `ADMIN`; `ADMIN`, `EDITOR`, and `VIEWER` semantics SHALL preserve the approved legacy matrix.
3. Each domain service SHALL authorize normal requests from its local versioned membership projection.
4. Unsafe mutations SHALL fail closed when the projection is missing or stale beyond policy.
5. Sensitive reads SHALL fail closed or use a deadline-bound emergency Tree Access lookup; no path SHALL fail open.
6. Membership revocation and stale-role behavior SHALL have explicit latency objectives, alerts, reconciliation, and security tests.

### Requirement 7: Domain Service Behavior

**User Story:** As a genealogy user, I need useful legacy domain behavior preserved within explicit service boundaries.

#### Acceptance Criteria

1. Member Service SHALL preserve member profile, lifespan/status validation, duplicate/merge internals, tombstones, and read-only legacy avatar fallback.
2. Relationship Service SHALL preserve canonical relationship keys, duplicate prevention, cycle safety, generation, ancestry, spouse, and adoption algorithms while serializing graph commands by `treeId` and aggregate version.
3. Event Service SHALL preserve CRUD, deterministic ordering, recurrence, leap-day behavior, and local event-member references without cross-service foreign keys.
4. Media & Album Service SHALL own media/album metadata, associations, upload intents, quarantine, mandatory scanning, thumbnails, tombstones, retention, cleanup, and binary replication.
5. Sharing Service SHALL own hashed links, expiry/revocation, allowlisted public projections, and token-plus-media-ID access; revocation SHALL take effect synchronously in that service.
6. Search & Reporting Service SHALL own MySQL read models for Vietnamese search, autocomplete, statistics, and reports; coherent outputs SHALL use revision barriers.
7. Domain parity SHALL be demonstrated with shared golden/property tests without requiring cross-service atomicity.

### Requirement 8: Import, Export, Snapshot, and Restore

**User Story:** As a user or operator, I need transfer workflows to avoid exposing partial multi-service state.

#### Acceptance Criteria

1. Transfer Service SHALL own import/export, generated artifacts, application snapshots, and restore orchestration; parsing/rendering SHALL run in isolated bounded workers.
2. GEDCOM/JSON/CSV preview SHALL persist no domain changes and preserve existing size, validation, and diagnostic constraints.
3. Import/replace SHALL parse and stage, validate a cross-domain manifest, freeze the tree, distribute writes under an epoch, verify counts/hashes, coordinate per-service activation, and unfreeze.
4. Restore SHALL create a safety snapshot, freeze, stage every domain, verify, coordinate epoch activation, reconcile binaries, and unfreeze.
5. Partial state SHALL NOT become visible; pre-activation failure SHALL remove staging, and post-partial-activation failure SHALL enter an explicit rollback or `MANUAL_REVIEW` workflow.
6. Reports/exports SHALL wait for required watermarks or return `202`/a stable stale-projection error rather than produce incoherent output.

### Requirement 9: Migration, Cutover, and Reconciliation

**User Story:** As a release owner, I need deterministic migration with safe cohort cutover and rollback.

#### Acceptance Criteria

1. Extraction SHALL be read-only, paginated, immutable, checksummed correctly, and preserve source IDs/timestamps and raw source manifests.
2. Transformation SHALL produce service-specific manifests and explicitly quarantine malformed, duplicate, cyclic, or cross-tree input.
3. Loading SHALL be idempotent per service; reconciliation SHALL cover counts/IDs, dangling references, graph hashes, watermarks, search/report samples, and binary inventory.
4. Cutover SHALL occur first for global identity and then by tree cohort using freeze, final delta, reconciliation, compare-and-set authority, and unfreeze.
5. Shadow reads MAY compare normalized responses; shadow mutations SHALL be prohibited.
6. Cutover SHALL auto-stop on dual writers, security events, blocking discrepancy, SLO breach, excessive consumer lag, or Saga failure.
7. Legacy writers SHALL remain until two production-like rehearsals, rollback-window expiry, zero traffic, and reverse-export validation succeed.

### Requirement 10: Platform, Security, and Operations

**User Story:** As an operator, I need a secure managed platform that supports independent service deployment and recovery.

#### Acceptance Criteria

1. Production SHALL run on managed multi-AZ Kubernetes with isolated namespaces/service accounts, NetworkPolicy, workload identity/mTLS, autoscaling, disruption budgets, and topology spread.
2. Managed MySQL 8.4 SHALL provide private endpoints, HA, encrypted backups, binlogs, PITR, and credentials isolated per service.
3. Managed Kafka-compatible infrastructure SHALL provide multi-AZ availability, Schema Registry, topic ACLs, encryption, and DR procedures.
4. Managed secrets/KMS, GitOps/IaC, signed OCI images, SBOM/provenance, non-root read-only runtime, and immutable digest promotion SHALL be required.
5. OpenTelemetry traces, structured logs, metrics, dashboards, alerts, and runbooks SHALL cover HTTP, Saga, outbox, consumer lag, projection freshness, databases, Blob, and migration.
6. Security SHALL target OWASP ASVS 5.0 Level 2 and cover IDOR/cross-tree access, CSRF, injection, token/file abuse, event PII leakage, retention, legal hold, and erasure.
7. Availability SHALL target 99.9%; normal reads p95 <300 ms, accepted mutation p95 <500 ms, and search p95 <200 ms; Saga-completion and projection-lag SLOs SHALL be approved before production writes.
8. DR drills SHALL verify per-service MySQL recovery, Kafka recovery/replay, projection rebuild, ordered system restore, and independent binary archive recovery.
9. Architecture tests SHALL prohibit cross-service database access, shared domain models, synchronous call cycles, and unversioned events.
10. Kubernetes, Helm, Terraform, and Argo CD SHALL be the mandatory vendor-neutral platform toolchain; cloud and managed MySQL, Kafka, KMS, and observability products SHALL require an environment ADR before provisioning.

### Requirement 11: Standardized Technology and Project Baseline

**User Story:** As an engineering team, we need reproducible service builds and explicit dependency boundaries so each service can be delivered independently.

#### Acceptance Criteria

1. The initial target baseline SHALL be Java 25 LTS, Spring Boot 4.1.x, and Maven Wrapper, subject to a dependency-resolution and build compatibility gate before service scaffolding.
2. If that gate fails because the selected artifacts or toolchain are unavailable or incompatible, implementation SHALL stop until a superseding ADR selects the nearest compatible Spring Boot GA baseline.
3. Services SHALL use Spring MVC, Spring Security, Spring for Apache Kafka, gRPC Java, Resilience4j, Micrometer/OpenTelemetry, Spring Data JDBC, jOOQ, and Flyway; tests SHALL use JUnit 5, AssertJ, Mockito, Testcontainers, and ArchUnit.
4. Flyway migrations SHALL be schema authority. Spring Data JDBC SHALL serve aggregate persistence, jOOQ SHALL serve explicit SQL and projection queries, and jOOQ generation SHALL be reproducible from the Flyway-defined schema.
5. Protobuf SHALL define both event schemas and gRPC service contracts. Event-contract and gRPC-contract artifacts SHALL be independently versioned and published; Schema Registry backward-compatibility policy SHALL govern event schemas.
6. The monorepo SHALL contain independently deployable services, each with its own `pom.xml`, `mvnw`, pipeline, image, Flyway migrations, and Helm chart; it SHALL NOT contain a root Maven parent/reactor that builds all services.
7. A service SHALL build and release without building another service and SHALL NOT import another service's source or domain model. Platform starters, API contracts, event contracts, and gRPC contracts SHALL be versioned published artifacts rather than shared domain source.
8. Saga state machines SHALL reside in the owning service; no external workflow engine SHALL be part of the initial baseline.
9. Each independently deployable service SHALL use a feature-oriented layered architecture. Within each feature, inbound REST controllers, Kafka consumers, and gRPC endpoints SHALL invoke the service layer, and the service layer SHALL invoke repositories and feature-local outbound integrations; inbound components SHALL NOT bypass the service layer to call repositories.
10. Transport request, response, and message DTOs and their mappings SHALL remain at inbound boundaries. Domain entities, value objects, and rules SHALL NOT depend on controllers, consumers, endpoints, or transport DTOs.
11. The service layer SHALL own use-case orchestration, transaction boundaries, authorization coordination, owning-service Saga logic, and calls to Kafka producers and external gRPC clients.
12. Repositories SHALL encapsulate Spring Data JDBC and jOOQ persistence. Service classes SHALL NOT use `DSLContext`, generated jOOQ types, or Spring Data repository implementations directly.
13. Cross-feature collaboration within a service SHALL use feature service APIs or events and SHALL NOT access another feature's repositories, internal transport DTOs, or persistence implementation. ArchUnit tests SHALL enforce these local layer and feature boundaries in addition to cross-service isolation.
14. Reusable platform starters SHALL provide technical infrastructure only and SHALL NOT impose generic architecture abstractions or contain shared business models.

## Out of Scope for Initial Migration

- Moving binary authority away from private Vercel Blob
- Frontend UX redesign beyond required async operation handling
- Native-image delivery
- OpenSearch without a benchmark and ADR
- Public duplicate-member/merge or audit-history APIs without approved contracts
