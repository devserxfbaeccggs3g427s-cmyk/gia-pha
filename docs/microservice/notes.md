# Microservice Decomposition — Research Notes

Running journal for the microservice-decomposition research lane. Each
phase appends a short section. Captured in `comparison.md` (Phase 7.2)
and `blog.md` (Phase 7.3).

> The original monolith spec at `.kiro/specs/spring-boot-backend-migration/`
> is the authoritative production baseline. This file documents lessons
> from the parallel research lane only.

## Final structure (Phase 7.4)

| What | Where | Notes |
|---|---|---|
| 332 Java files | `services/*/src/{main,test}/java` | domain + adapters + tests |
| 12 Maven modules | `services/{service-common, identity, audit, tree, members, relationships, events, media-metadata, binary-storage, sharing, reporting, transfer, gateway}-service/` | each with own `pom.xml`, `application.yml`, `Dockerfile`, Flyway migrations |
| Flyway migrations | `services/*/src/main/resources/db/migration/V*.sql` | per-service outbox + saga log + domain tables (split from monolith) |
| Infra | `infra/docker-compose.yml` + `mysql/init/01-schemas.sql` | MySQL 8.4 + RabbitMQ + Prometheus + Grafana + 12 services + per-service MySQL users |
| Docs | `docs/microservice/` | ADR-101..105, notes, comparison, blog, security, observability, runbooks, sagas |

## Migration strategy (revised twice)

The decomposition initially tried to reuse `backend/<module>` JARs
through Maven coordinates. That approach was rejected because the
monolith's `tree-content` is one Maven artifact owning all five
tree-content aggregates — services would share classes at compile-time.

The current strategy: **copy** each `backend/<module>` source file into
the appropriate `services/<svc>/` module under a non-hyphenated Java
package. Domain code is now identical to the monolith in semantics
but lives in independent modules. Each service has:

- A non-hyphenated Java package (`vn.giapha.research.<svc>`).
- Its own `pom.xml` with `<dependency>` on `service-common`.
- Its own `application.yml` (database, RabbitMQ host, Flyway schema).
- Its own `Dockerfile`.
- Its own Flyway migrations under `db/migration/`.

## Frontend integration (Phase 7.4 — completed)

The 31 Next.js API routes now proxy through the Spring Cloud Gateway.
Details: [`frontend-integration.md`](frontend-integration.md).

Quick summary:
- 31/31 routes rewritten to call `gateway-service:8080` instead of
  reading Vercel Blob JSON files.
- 12 service modules in `src/lib/services/*` use `springFetch` from
  `src/lib/api/spring-client.ts`.
- DTO mapper (`src/lib/api/dto-mapper.ts`) translates between
  microservice response shape and the legacy frontend types.
- NextAuth sessions still read from Blob (legacy adapter); identity
  writes go through the gateway.

## Coverage verification

Every client-side data fetch in the frontend routes through the BFF
adapter, which then routes through the gateway:

| Surface | Mechanism | API path |
|---|---|---|
| React Query hooks (`useGenealogyQueries`) | `apiRequest()` → `/api/...` | `/api/trees/{}/members`, `/api/trees/{}/events`, etc. |
| Mutation hooks (`useMemberMutation`, etc.) | `apiRequest()` → `/api/...` | `/api/members/{Id}`, `/api/trees/{}/{action}`, etc. |
| Direct `fetch()` in components | `/api/...` | `/api/trees`, `/api/media/upload`, `/api/import/preview`, `/api/import/execute`, `/api/auth/register` |
| Server-side pages | none (pure rendering) | — |
| NextAuth session lookup | `VercelBlobAdapter` (legacy) | (NextAuth DB adapter — see follow-up) |

`grep -E "fetch\(['\"]/api" src/` returns 5 unique paths; `grep -E "apiRequest\(`/api" src/` returns 14 unique paths. All 21 client-side fetch calls go through the BFF → gateway → microservice chain.

The only remaining direct-Blob reads are NextAuth's own DB adapter
(used internally by NextAuth for OAuth account / session persistence).
This is a follow-up task: implement `IdentityClientAdapter implements Adapter`
that calls `identity-service` instead of Blob.



The decomposition initially tried to reuse `backend/<module>` JARs
through Maven coordinates. That approach was rejected because the
monolith's `tree-content` is one Maven artifact owning all five
tree-content aggregates — services would share classes at compile-time.

The current strategy: **copy** each `backend/<module>` source file into
the appropriate `services/<svc>/` module under a non-hyphenated Java
package. Domain code is now identical to the monolith in semantics
but lives in independent modules. Each service has:

- A non-hyphenated Java package (`vn.giapha.research.<svc>`).
- Its own `pom.xml` with `<dependency>` on `service-common`.
- Its own `application.yml` (database, RabbitMQ host, Flyway schema).
- Its own `Dockerfile`.
- Its own Flyway migrations under `db/migration/`.

After the migration, `backend/` was deleted and the
`tools/codegen/` migration scripts were also removed (they were
one-shot). The remaining templates `tools/codegen/V1__outbox.sql`
and `V2__saga_log.sql` are kept for any future service addition.

## Service-to-domain mapping

| Service | Owns (Java package) | Schema | Backend module migrated |
|---|---|---|---|
| `identity-service` | `vn.giapha.research.identity.*` | `identity` | `identity-access` |
| `audit-service` | `vn.giapha.research.audit.*` | `audit` | `audit-operations` |
| `tree-service` | `vn.giapha.research.tree.*` | `tree_content` | `tree-content` (FamilyTree, TreeMembership subset) |
| `members-service` | `vn.giapha.research.members.*` | `tree_content` | `tree-content` (Member subset) |
| `relationships-service` | `vn.giapha.research.relationships.*` | `tree_content` | `tree-content` (Relationship subset) |
| `events-service` | `vn.giapha.research.events.*` | `tree_content` | `tree-content` (Event subset) |
| `media-metadata-service` | `vn.giapha.research.media.*` | `tree_content` | `tree-content` (MediaObject, Album subset) |
| `binary-storage-service` | `vn.giapha.research.binarystorage.*` | `binary_storage` | `binary-storage` |
| `sharing-service` | `vn.giapha.research.sharing.*` | `sharing` | `sharing` |
| `reporting-service` | `vn.giapha.research.reporting.*` | `reporting` | `reporting-search` |
| `transfer-service` | `vn.giapha.research.transfer.*` | `transfer` | `transfer` |
| `gateway-service` | `vn.giapha.research.gateway.*` | n/a | (no domain code; pure Spring Cloud Gateway routing) |
| `service-common` | `vn.giapha.research.service.*` | n/a | `platform-kernel` + `app-bootstrap` infrastructure |

## Architecture summary

```
Next.js BFF -> gateway-service (Spring Cloud Gateway, :8080)
                    |
                    +-- identity-service      (:8081, schema `identity`)
                    +-- audit-service         (:8082, schema `audit`)
                    +-- tree-service          (:8083, schema `tree_content`)
                    +-- members-service       (:8084, schema `tree_content`)
                    +-- relationships-service (:8085, schema `tree_content`)
                    +-- events-service        (:8086, schema `tree_content`)
                    +-- media-metadata-service(:8087, schema `tree_content`)
                    +-- binary-storage-service(:8088, schema `binary_storage`)
                    +-- sharing-service       (:8089, schema `sharing`)
                    +-- reporting-service     (:8090, schema `reporting`)
                     +-- transfer-service      (:8091, schema `transfer`)
```

```
Next.js BFF -> gateway-service (Spring Cloud Gateway, :8080)
                    |
                    +-- identity-service     (:8081, schema `identity`)
                    +-- audit-service        (:8082, schema `audit`)
                    +-- tree-service         (:8083, schema `tree_content`)
                    +-- members-service      (:8084, schema `tree_content`)
                    +-- relationships-service(:8085, schema `tree_content`)
                    +-- events-service       (:8086, schema `tree_content`)
                    +-- media-metadata-service(:8087, schema `tree_content`)
                    +-- binary-storage-service(:8088, schema `binary_storage`)
                    +-- sharing-service      (:8089, schema `sharing`)
                    +-- reporting-service    (:8090, schema `reporting`)
                    +-- transfer-service     (:8091, schema `transfer`)
```

The five services that own the `tree_content` schema each use a
**separate MySQL user** (per Requirement 13.5: no shared service account).
Each service declares its own Flyway schema; HBM2DDL is `validate` so
schema drift is caught at boot.

## Code reuse strategy (revised after migration)

The decomposition initially reused `backend/<module>` JARs through Maven
coordinates. That approach was rejected after the first end-to-end run
revealed a critical issue: **the monolith's `tree-content` module is a
single Maven artifact owning all five tree-content aggregates**
(members, relationships, events, media, family trees). If every
sub-service depends on `vn.giapha:tree-content`, the split is
illusory — they share classes at compile-time and the JPA repository
classes don't enforce schema-per-service.

The current strategy: **copy** the relevant files from `backend/` into
each `services/<svc>/` module, under a non-hyphenated Java package
(`vn.giapha.research.<svc>.<layer>`). The copy is one-shot — there is
no Maven dependency on the backend artifact from any service anymore.

Migration scripts live in `tools/codegen/`:
- `migrate-tree-content.sh` — splits `backend/tree-content` into 5
  service modules (tree, members, relationships, events, media) by
  aggregate (per ADR-101).
- `migrate-backend-modules.sh` — copies identity, audit, binary,
  sharing, reporting, transfer.
- `fix-service-poms.sh` — removes the backend `<dependency>`, adds
  platform-kernel + jdbc + validation + per-service starters.
- `rewrite-cross-service-imports.sh` — splits `vn.giapha.tree.*`
  imports across the 5 sub-services based on aggregate.
- `dedupe-poms.sh` — cleans up duplicate `<dependency>` blocks after
  multi-pass runs.

After migration, each service has:
- Domain classes in its own package.
- JdbcClient-backed repositories in `adapter.out.mysql`.
- Application services + controllers in their own packages.
- A `V1__outbox.sql` Flyway migration (per-service outbox table).
- A `V2__saga_log.sql` migration for orchestrator services.

The monolith is **not** modified. Both worlds coexist; backend/ stays
the production baseline.

## Cross-service facades

After migrating, services that need to read data owned by another
service (sharing, audit, reporting) used to import `vn.giapha.tree.*`
domain classes directly. That violates ADR-101 (no cross-service
domain coupling). The fix is the `service-common.clients` package:

- `IdentityClient` — implemented by `identity-service`; exposes
  `isKnownUser(userKey)` and `resolveSession(sessionId)`. No password
  hash leaves identity-service.
- `TreeClient`, `MemberClient`, `RelationshipClient`, `EventClient` —
  implemented by their respective owning service; expose narrow DTOs
  (`ClientDtos.FamilyTreeSummary`, `MemberSummary`, etc.) so consumers
  can't accidentally couple to internal fields.
- `UserContext` — the bridge-token (JWT) header used by the gateway.

For the research lane, the client implementations read directly from
the local MySQL schema. The MySQL user for sharing/audit/reporting has
SELECT-only grants on the `tree_content` schema. This is acceptable
for a personal-project decomposition; a production system would route
through REST or RabbitMQ commands instead.

## Outbox rewrite

Originally every service called `audit-service`'s
`EnqueueOutboxUseCase` to record an audit + outbox row in the same
transaction. That breaks the per-service outbox invariant
(Requirement 2.3). The refactor:

- Each service has its own `outbox` table (Flyway `V1__outbox.sql`).
- Each service writes to its own outbox via
  `OutboxRelay.append(eventType, aggregateId, treeKey, payload)` from
  service-common.
- audit-service consumes the per-service outbox tables indirectly via
  RabbitMQ subscriptions — it does NOT own the cross-service outbox
  any more.

This is closer to what design.md describes: "Outbox writes SHALL be
appended in the same local transaction as the domain change." Local
transaction = the service's own DB session.

## Phase 0 — Foundation

### Phase 0.1 — Infra docker-compose

- **Decisions**
  - One docker-compose file at `infra/docker-compose.yml` covers MySQL
    8.4, RabbitMQ (with management UI), Prometheus, Grafana, and all 12
    services. This is **not** the production compose; that lives in
    `backend/`.
  - Per-service databases are created in `infra/mysql/init/01-schemas.sql`,
    with separate MySQL users per service (Requirement 13.5: no shared
    service account). Passwords are read from `.env.example` and never
    committed.
  - The local CA is generated procedurally by `tools/ca/make-ca.sh` so
    the repo stays clean. Certs are git-ignored.
- **Surprises**
  - zsh's heredoc parsing of `${X:-default}` interfered with codegen
    scripts that wanted literal placeholders in YAML. Switched to
    Python-rendered templates — see `tools/codegen/`.
- **Dead-ends**
  - Considered a separate docker-compose per phase; rejected because
    Phase 7's benchmark needs both topologies in the same docker context.

### Phase 0.2 — Shared libraries

- **Decisions**
  - No separate `libs/` Maven reactor. Instead, `services/service-common`
    is a Maven module that holds all cross-service infrastructure
    (RabbitMQ topology, request-id filter, outbox relay, global error
    handler, configuration properties, saga orchestrator, observability
    tags, user-context filter).
  - Domain contracts (event envelopes, command contexts) live alongside
    `service-common` rather than as a separate jar. This keeps the
    surface small and avoids publishing `contract-types` as its own
    release line.
- **Why not 4 separate libs?**
  - The spec's Phase 0.2 lists four libraries. We collapsed `libs/clock`,
    `libs/contract-types`, `libs/saga-framework`, `libs/outbox-relay`,
    `libs/tracing` into a single `service-common` module because:
    1. The `backend/` monolith already owns the abstractions that would
       otherwise live in those libraries (outbox table, error handling,
       JSON envelope, Clock). Duplicating them would create a fork.
    2. The research lane's goal is to compare topologies, not to
       maintain a parallel infrastructure library.
  - Trade-off: services share `service-common` at compile-time. A real
    decomposition with independent versioning would split these out.

### Phase 0.3 — `@TransactionalOutbox` and outbox relay

- **Decisions**
  - The relay lives in `service-common.OutboxRelay` and is driven by
    `OutboxScheduler` (`@Scheduled`).
  - Each service declares its own `OutboxRowDao` implementation in a
    Flyway migration; the relay code is identical across services.
  - The shared `JdbcOutboxRowDao` reads the per-service `outbox` table
    (Flyway `V1__outbox.sql`). Services can override by providing their
    own `@Repository` bean.
  - Publisher confirms (mandatory delivery) are set on `RabbitTemplate`
    in `ServiceRabbitConfiguration`.
  - Event routing: the relay maps the event type prefix
    (`identity.MemberCreated` -> `events.identity`) to the corresponding
    exchange in `RabbitTopology`. This is the fan-out rule design.md
    requires.
  - Outbox signing (Phase 6.4): the payload's `signature` column is
    filled by the service's signer; consumers verify before applying.

## Phase 1 — Identity, audit, gateway

### Phase 1.1 — identity-service

- **Status:** service skeleton complete. Domain code (controllers,
  Argon2id password hashing, OAuth accounts, verification tokens,
  lockout policy, sessions) is reused from `backend/identity-access`
  through Maven dependency. No source duplication.
- **Open work:** live health probe; lockout-policy test under 5 rapid
  failed attempts (Definition of Done for 1.1).

### Phase 1.2 — audit-service

- **Status:** service skeleton complete. Reuses `backend/audit-operations`
  (idempotency, business + security audit schemas, outbox fan-in).
- **Open work:** wire fan-in consumers to consume the per-service
  exchanges declared in `RabbitTopology`. With both `events.identity`
  and the rest bound, audit replay becomes the safety net behind the
  outbox at-least-once guarantee (Requirement 8.1, 2.4).

### Phase 1.3 — gateway-service

- **Status:** Spring Cloud Gateway skeleton with routes for all 11
  backend services. Session validation → JWT forward lands in
  Phase 6.4 (security baseline) so it ships together with mTLS.

## Phase 2 — Tree content decomposition

### ADR-101 outcome

Strategy B (split by aggregate) was chosen. The five sub-services —
`tree-service`, `members-service`, `relationships-service`,
`events-service`, `media-metadata-service` — all share the
`tree_content` schema but use different MySQL users and write to
non-overlapping tables.

Documented consequence (design.md): no single tree-row lock. Concurrent
graph mutations are serialised only within `relationships-service`.

## Phase 3 — Binary pipeline

`binary-storage-service` reuses `backend/binary-storage` (upload
intents, scan orchestration, replicas, cleanup). The activation saga
with `media-metadata-service` is wired in Phase 6.1.

## Phase 4 — Sharing and reporting

- `sharing-service` consumes tree-content events to rebuild the public
  projection within 30 s.
- `reporting-service` consumes every `*Created`/`*Updated`/`*Deleted`
  event and rebuilds `search_index`. Vietnamese search parity is
  inherited from `backend/reporting-search`.

## Phase 5 — Transfer

`transfer-service` owns import/export/snapshots. Import orchestration
saga handles compensation per design.md §Sagas.

## Phase 6 — Cross-cutting

### Phase 6.1 — Saga framework wiring

- Each orchestrator service gets `service-common` + a local
  `SagaStateLog` JDBC implementation.
- Sagas are declared in `services/<orchestrator>/src/main/java/.../saga/`
  and registered as Spring beans.
- The 11 sagas listed in design.md §Sagas each get a runbook entry in
  `docs/microservice/sagas/`.

### Phase 6.2 — Reconciliation

- Per-service reconciliation job runs every 5 min.
- Drift reports go to the audit-service `security_audit` table.

### Phase 6.3 — Observability

- OpenTelemetry SDK config in `service-common.observability`.
- Prometheus scrapes each service's `/actuator/prometheus`.
- Grafana dashboards in `infra/grafana/dashboards/`:
  - `services-overview.json` (per-service p95 latency, error rate)
  - `outbox-lag.json`
  - `saga-duration.json`

### Phase 6.4 — Security baseline

- mTLS via the local CA (Requirement 13.1).
- Outbox event signing — see `docs/microservice/security.md`.
- Actuator endpoints gated by default (`management.endpoint.health.show-details=when_authorized`).

## Phase 7 — Research deliverables

### Phase 7.1 — Benchmark

`tools/bench/run.sh` runs the same scripted workload against:
1. The `backend/app-bootstrap` monolith on a single MySQL schema.
2. The microservice decomposition on the per-service docker-compose.
Output: `docs/microservice/bench.json` + `bench.csv`.

### Phase 7.2 — Comparison

`docs/microservice/comparison.md` populates a trade-off matrix.
Research lane is honest about research limitations (single laptop,
single replica per service, no real load).

### Phase 7.3 — Blog write-up

`docs/microservice/blog.md` distils the lessons: the cost of giving up
same-DB transactions; how often the cycle-detection invariants
actually fail in practice; whether mTLS overhead is noticeable.

### Phase 7.4 — Final notes

This file, post-Phase 7. Captures every dead-end, every divergence,
every surprise.

## Open questions (carry over)

- Should the `tree_content` schema be split into 5 schemas (one per
  sub-service)? The current setup keeps one schema with separate MySQL
  users. The next research question is whether DB-level isolation
  matters for the failure-mode story.
- Should the outbox table be one table per service (current) or one
  shared table with a `source` column? Current design chose per-service
  for blast-radius reasons.
