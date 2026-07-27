# Session Handoff — Spring Boot Microservices Migration

> **Generated:** 2026-07-27 13:35 (ICT)
> **Repo:** `/Users/os_ngocnq/IdeaSnapshots/gia-pha`
> **Branch:** `master`
> **Spec:** `.kiro/specs/spring-boot-backend-migration/`

## 1. Project Overview

### Goal
Migrate a **Next.js + Prisma + MySQL** family-tree monolith (`src/`, `prisma/`) to **12 independent Spring Boot 4.1 microservices** on **Java 25**, communicating via **Kafka** with **DB-per-service**, deployed to a **managed Kubernetes** (vendor-neutral — AWS-first implementation, portable to GCP/Azure). The architecture is governed by 12 ADRs, 1 design doc, and 20 numbered tasks.

### Scope Decision (current session)
**Full monorepo scaffold** — Tasks 1–5 only:
1. Supersede architecture spec (governance + scanners)
2. Freeze legacy + publish new contracts (OpenAPI, Protobuf, fixtures)
3. (no Task 3 in numbering) — see Task 2A
4. **Task 2A** — Technology baseline + independent monorepo layout
5. **Task 3** — Managed platform foundation (Terraform, Helm, Argo CD)
6. **Task 4** — Spring service template (platform starters + 11 service skeletons)
7. **Task 5** — **Identity reference service** end-to-end (domain → adapters → runbooks)

> **Out of scope:** Tasks 6–20 (member, tree-access, relationship, recurrence, transfer, sharing, media, search, redaction, migration, audit-ops, api-gateway, cutover, DR, pen-tests, ops). These follow the Identity pattern.

### Constraints & Preferences
- Senior Backend Engineer persona; modern Spring Boot best practices.
- **No unit tests** in this phase — **ArchUnit only** (architecture tests).
- **Independent monorepo**: each service owns its own `pom.xml`, `mvnw`, Flyway migrations, Helm chart, pipeline, Dockerfile, OWNERS. **No root Maven reactor**.
- Architecture-scanner rules must pass in CI on every PR.
- Cross-service mutations return **HTTP 202** + `AsyncOperation` envelope; polled via `GET /api/v2/operations/{operationId}`.
- `Idempotency-Key` required on all mutating calls; ETag/If-Match on reads; stable error envelope with `traceId`.

## 2. Tech Baseline (ADR-009)

| Layer | Choice | Version |
|---|---|---|
| JDK | Eclipse Temurin LTS | **25** |
| Framework | Spring Boot | **4.1.0** |
| Web | spring-boot-starter-web (REST + error handling) | bundled |
| Messaging | spring-kafka | 4.0.0 |
| RPC | grpc-netty-shaded + grpc-protobuf | 1.70.0 |
| Schemas | protobuf + protoc-gen-grpc-java | 3.25.5 |
| Resilience | resilience4j-spring-boot3 | 2.3.0 |
| Metrics | micrometer-* + opentelemetry-* | 1.14.0 / 1.44.0 |
| Migrations | flyway-mysql | 11.0.0 |
| Query | jOOQ (read model) | 3.19.15 |
| Persistence | spring-data-jdbc (write model) | 4.0.0 |
| Driver | mysql-connector-j | 9.0.0 |
| Security | spring-security-crypto (BCrypt cost 10) | bundled |
| Tests | junit-jupiter / assertj / archunit / testcontainers | 5.11.0 / 3.26.3 / 1.3.0 / 1.20.4 |

Baseline verified by `platform/ci/baseline-spike.sh` → verdict **PASS** in `baseline-spike.json`.

## 3. What's Delivered (502 files, 89 Java files)

### Governance & Spec
```
.kiro/specs/spring-boot-backend-migration/
├── requirements.md, design.md, tasks.md
├── adrs/ADR-001..012   (12 ADRs)
└── governance/
    ├── README.md
    ├── archive/README.md
    ├── task-1-supersede-architecture.md
    ├── task-2-freeze-legacy-publish-contracts.md
    ├── task-2a-technology-baseline.md
    ├── task-3-platform-foundation.md
    ├── task-4-service-template.md
    ├── task-5-identity-reference-service.md
    └── traceability-matrix.md
```

### Architecture Scanners (must pass in CI)
```
tooling/architecture-scan/
├── ArchitectureScan.java       (no-modular-monolith, no-cross-service-FK, no-sync-success)
├── NoRootReactorTest.java
├── CrossServiceImportTest.java
└── manifest.yaml
```
**All green.** Excludes: `.kiro/specs`, `.kilo`, `.idea`.

### Contracts
```
contracts/
├── api/v2/openapi.yaml                  (202 envelope, operation polling, ETag, idempotency)
├── grpc/identity/IdentityLookup.proto
├── events/catalog.yaml                  (11 primary + 10 DLQ topics)
├── events/identity/IdentityUserCreated.proto
├── frontend-legacy-inventory.yaml
├── frontend-migration.yaml
└── fixtures/{identity,genealogy,recurrence,search-vi,redaction,import,export}/
```

### Platform Starters (7 modules under `platform/starters/`)
```
platform-spring-boot-starter/        26 Java files — AsyncOperation, ErrorResponse,
                                      OperationController, GlobalErrorHandler,
                                      DomainException hierarchy, IdempotencyStore,
                                      OutboxWriter + JdbcOutboxWriter + OutboxRelay,
                                      InboxStore + JdbcInboxStore, KafkaTopicConfig,
                                      KafkaConsumerConfig, ObservabilityConfig,
                                      PlatformMetrics, PlatformHealthIndicator,
                                      PlatformProperties, PlatformAutoConfiguration
platform-outbox-starter/             outbox relay + writer wiring
platform-resilience-starter/         resilience4j defaults
platform-observability-starter/      OTel + Micrometer wiring
platform-security-starter/           BCrypt PasswordEncoder, SecurityDefaultsConfig,
                                      BridgeTokenIssuer (legacy SSO bridge)
platform-grpc-starter/               GrpcServerConfig, TracingClientInterceptor,
                                      TracingServerInterceptor
```
Each starter ships: `pom.xml`, `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, `application.yml`, `package-info.java`.

### Platform Foundation
```
platform/
├── starters/bom/pom.xml              (Java 25 / Boot 4.1.0 + pinned deps)
├── ci/baseline-spike.sh, baseline-spike.json, ci.yml
└── terraform/
    ├── modules/{managed-kubernetes, managed-mysql, managed-kafka,
    │             managed-kms, managed-observability}/main.tf
    └── envs/example/main.tf

platform/helm/
├── Chart.yaml, values.yaml
└── templates/{_helpers.tpl, deployment.yaml, hpa.yaml, pdb.yaml,
               networkpolicy.yaml, servicemonitor.yaml, service.yaml}
```

### Managed Kafka Topics (ADR-004)
- **11 primary:** `identity.events.v1`, `member.events.v1`, `tree-access.events.v1`, `relationship.events.v1`, `recurrence.events.v1`, `transfer.events.v1`, `sharing.events.v1`, `media.events.v1`, `search.events.v1`, `redaction.events.v1`, `audit.events.v1` — 12 partitions, RF=3, 7y retention, zstd, `min.insync.replicas=2`.
- **10 DLQ:** `<primary>.events.v1.dlq` — 6 partitions, 14d retention.
- Required headers per event: `event_id, correlation_id, causation_id, operation_id, aggregate_version, occurred_at, schema_version, traceparent`.

### Service Skeletons (11 + 1 reference)
```
services/
├── api-gateway-service/      (Task 6)
├── audit-ops-service/        (Task 13)
├── event-service/            (Task 17 — read-model projector)
├── identity-service/         ★ TASK 5 — full reference impl
├── media-service/            (Task 16)
├── member-service/           (Task 7)
├── migration-service/        (Task 19 — one-time importer)
├── relationship-service/     (Task 8)
├── search-service/           (Task 15)
├── sharing-service/          (Task 12)
├── transfer-service/         (Task 11)
└── tree-access-service/      (Task 9)
```
Each has: `pom.xml`, `mvnw*`, `application.yml`, `OWNERS`, `Dockerfile`, `pipeline/build.yml`, `deploy/helm/{Chart.yaml,values-prod.yaml,configmap.yaml}`, `src/main/java/com/familya/<pkg>/`, `src/test/java/com/familya/<pkg>/archunit/ArchitectureTest.java`.

### Identity Reference Service (32 Java files)
```
services/identity-service/src/main/java/com/familya/identity/
├── IdentityServiceApplication.java        (Clock + java.time.Clock beans)
├── domain/
│   ├── model/{User,Session,OAuthLink}.java
│   ├── events/{IdentityEvent,IdentityUserCreated,IdentityUserVerified,
│   │           IdentityUserLocked,IdentityCredentialsRehashed}.java
│   └── exceptions/{EmailAlreadyExists,AccountLocked,InvalidCredentials,
│                  RateLimitExceeded}.java
├── application/
│   ├── port/in/{RegisterUserCommand,VerifyEmailCommand,AuthenticateCommand,
│   │            RevokeSessionCommand}.java
│   ├── port/out/{IdentityRepository,IdentityEventPublisher,
│   │             RegistrationRateLimiter}.java
│   └── usecase/{RegisterUserUseCase,VerifyEmailUseCase,AuthenticateUseCase,
│                RevokeSessionUseCase}.java
├── adapters/
│   ├── persistence/{JdbcIdentityRepository,JdbcRegistrationRateLimiter}.java
│   ├── messaging/{OutboxIdentityEventPublisher,IdentityEventListener}.java
│   ├── rest/IdentityController.java
│   ├── grpc/IdentityLookupService.java
│   └── security/IdentitySecurityConfig.java
└── resources/db/migration/
    ├── V1__init_identity_schema.sql       (users, session, oauth_link,
    │                                        registration_attempt, verification_token)
    └── V2__outbox_inbox_idempotency_audit.sql

services/identity-service/infra/
├── runbooks/{identity-cutover.md, identity-credentials-rehash.md}
```
Behavior: BCrypt cost 10, rehash-on-login (ADR-005), rate-limit registrations via Redis-backed counter, async registration returns 202 + operationId, password rehashed transparently on successful login (forward-compatible to higher cost).

## 4. Key Decisions Locked In

| Topic | Decision | Reference |
|---|---|---|
| Architecture | 12 independent Spring Boot microservices, DB-per-service | ADR-002 |
| Communication | Kafka (mandatory) + gRPC (sync, capped at 2 hops) | ADR-002, ADR-003 |
| Cross-service mutation | HTTP 202 + AsyncOperation envelope | ADR-007 |
| Idempotency | `Idempotency-Key` header required; payload hash check | ADR-007 |
| Read consistency | ETag + If-Match; allow stale=true on critical reads | design.md §3 |
| Outbox pattern | `outbox_record` polled every 500ms, BATCH=100, LOCK_TTL=30s, at-least-once | platform-outbox-starter |
| Inbox dedup | PK `(event_id, consumer)`; `exists` before side effects | platform-spring-boot-starter |
| Auth | BCrypt cost 10, rehash on login; bridge token for legacy SSO | ADR-005 |
| Observability | OTel + Micrometer; `traceparent` + `x-correlation-id` required | platform-observability-starter |
| Platform | Managed K8s (EKS), RDS MySQL, MSK Kafka, KMS, CloudWatch | ADR-006, ADR-012 |
| Build | **No root reactor**; each service is independent; affected-service CI | ADR-011 |
| Schemas | Protobuf 3.25.5; Avro rejected (license) | ADR-010 |
| Vendor neutrality | Provider gates in CI (AWS first, GCP/Azure gated) | ADR-012 |

## 5. CI Gates (must pass on every PR)
1. `tooling/architecture-scan/` — ArchitectureScan + NoRootReactorTest + CrossServiceImportTest
2. `platform/ci/baseline-spike.sh` — tech baseline spike
3. Per-service: `./mvnw verify -P archunit` (ArchUnit only, no unit tests per scope)
4. Affected services only (per `tooling/affected-services.sh`)
5. Provider gate (ADR-012)

## 6. Critical Conventions for Next Sessions

### Must follow when adding new services
1. Run `./tooling/service-bootstrap.sh <service-name> <pkg-suffix> <port> <db-name>` — generates skeleton.
2. Edit the new service's `pom.xml` to depend on platform starters (copy from identity-service).
3. Use **jOOQ for reads**, **spring-data-jdbc for writes** — never Hibernate/JPA.
4. Use **package-private** scope inside `domain/`; `public` only at `application.port.*`.
5. All events go through **OutboxWriter**; consumers must use **InboxStore.exists/markProcessed**.
6. Every cross-service mutation returns `AsyncOperation`; controller never blocks on Kafka ack.
7. Flyway migrations live under `src/main/resources/db/migration/` (V1, V2, …).
8. Helm chart: `digest` field **REQUIRED** (not `image.tag`) per ADR-006.
9. OWNERS file must list 2+ maintainers.
10. Architecture tests: each service has `ArchitectureTest.java` enforcing layered package access.

### Naming conventions
- Package root: `com.familya.<service>` (e.g. `com.familya.identity`).
- Topic: `<context>.events.v<n>` (e.g. `identity.events.v1`).
- Proto package: `com.familya.<service>.events.v<n>`.
- Java event class: `<Domain><Verb>ed` past-tense (e.g. `IdentityUserCreated`).
- Operation ID: ULID, returned in `AsyncOperation.operationId`.

## 7. Known Limitations / Caveats

1. **No unit tests** in this scope (per user). ArchUnit only. Next phase should add test suites per service.
2. **Service skeletons are scaffolds** — only Identity is fully implemented. The other 11 have ArchUnit tests + skeleton code but no use cases yet.
3. **`platform/starters/bom/pom.xml` versions are pinned** — confirm against Maven Central before major-version bumps (Spring Boot 4.1 GA).
4. **gRPC streaming patterns** (server-streaming for projections) referenced in design.md but not yet prototyped — first use case likely in `event-service` (Task 17).
5. **Saga orchestrator** (`platform/saga-starter/`) not yet built — Tasks 11/12 (transfer, sharing) will require it. Plan as a 7th platform starter.
6. **Provider-gate CI** (ADR-012) defined but not wired into `platform/ci/ci.yml` yet — should be added when the first non-AWS provider (GCP) is gated.
7. **Legacy inventory** (`contracts/frontend-legacy-inventory.yaml`) is partial — flag routes as Next.js pages are progressively catalogued during migration.
8. **ArchitectureTest.java** in each skeleton currently has placeholder rules; real per-service layered-access rules should be tightened as code grows.

## 8. Suggested Next Tasks (priority order)

| # | Task | Notes |
|---|---|---|
| 6 | Member service (Task 7 in spec) | Next-largest surface after identity; uses tree-access events |
| 7 | Tree-access service (Task 9) | Heart of authorization projections (ADR-005); Redis cache |
| 8 | `platform-saga-starter` | Required before transfer (Task 11) and sharing (Task 12) |
| 9 | Relationship service (Task 8) | Event projector on member.events |
| 10 | Recurrence service (Task 10) | Pure domain; fewest cross-service deps |
| 11 | Transfer service (Task 11) | First saga — uses saga-starter |
| 12 | Sharing service (Task 12) | Second saga; gRPC streaming candidate |
| 13 | Media service (Task 16) | Object storage (S3) adapter; async uploads |
| 14 | Search service (Task 15) | OpenSearch / Meilisearch read-model |
| 15 | Redaction service (Task 18) | GDPR/CCPA right-to-erasure |
| 16 | Audit-ops service (Task 13) | Read-model of all `*.events.v1` |
| 17 | Event service (Task 17) | Centralized projector for cross-cutting read models |
| 18 | Migration service (Task 19) | One-time importer from legacy Prisma DB |
| 19 | API gateway (Task 6) | BFF composition + auth (Spring Cloud Gateway) |
| 20 | Cutover / DR / pen-tests / ops | Operational hardening |

## 9. Quick-start for the Next Session

```bash
cd /Users/os_ngocnq/IdeaSnapshots/gia-pha

# 1) Re-verify architecture scanners (should be green)
cd tooling/architecture-scan && mvn -q test && cd -

# 2) Re-verify a service skeleton (use Identity as the worked example)
cd services/identity-service
./mvnw -q -P archunit test    # ArchUnit only
./mvnw -q -DskipTests package

# 3) Bootstrap the next service
cd /Users/os_ngocnq/IdeaSnapshots/gia-pha
./tooling/service-bootstrap.sh member-service member 8082 family_member

# 4) Read the spec for context
cat .kiro/specs/spring-boot-backend-migration/tasks.md | head -200
cat .kiro/specs/spring-boot-backend-migration/design.md | head -200
```

## 10. Resume Prompt for New Session

If you start a fresh Kilo session and want to pick up exactly here, paste:

```
We are migrating /Users/os_ngocnq/IdeaSnapshots/gia-pha from Next.js+Prisma+MySQL
to 12 independent Spring Boot 4.1 (Java 25) microservices with Kafka + gRPC on
managed Kubernetes. The plan and current state are in
.kiro/specs/spring-boot-backend-migration/governance/SESSION-HANDOFF.md.

Already done: Tasks 1, 2, 2A, 3, 4, 5 (Identity reference service end-to-end).
Next: Task 7 (member-service) — full implementation following the Identity pattern.
Constraints: no unit tests in this phase, ArchUnit only, independent monorepo
(no root reactor), every cross-service mutation is HTTP 202 + AsyncOperation.
```

---

**Status:** ✅ Tasks 1–5 complete; Identity is the reference implementation; 11 service skeletons ready to flesh out.