# Task 1 — Supersede the Architecture Specification

## 1.1 Approval of authoritative baseline

The following documents constitute the **single effective architecture** for the
Family tree backend platform and supersede every prior modular-monolith
decision, including the previous ADR-001 and any in-tree description of one
shared tree-content transaction or database.

| Document | Path | Role |
|---|---|---|
| Requirements | `.kiro/specs/spring-boot-backend-migration/requirements.md` | Authoritative |
| Design | `.kiro/specs/spring-boot-backend-migration/design.md` | Authoritative |
| Tasks | `.kiro/specs/spring-boot-backend-migration/tasks.md` | Authoritative |
| ADRs | `.kiro/specs/spring-boot-backend-migration/adrs/ADR-00{1..12}-*.md` | Authoritative |

## 1.2 Superseded artefacts

The following items are **archived** and must not be used to drive new
implementation:

- Pre-migration modular-monolith decision log
- Single-DB tree-content transaction descriptions
- Any ticket, ADR, or runbook implying synchronous-success mutations across
  services
- Any legacy decision that required one Spring deployable

A copy of each superseded item, clearly stamped **SUPERSEDED — see
`.kiro/specs/spring-boot-backend-migration`**, is kept under
`.kiro/specs/spring-boot-backend-migration/governance/archive/` for audit
purposes only.

## 1.3 Requirement → Design → Task traceability

The matrix below maps every numbered requirement to the design sections that
implement it and to the task(s) that deliver it. A `?` cell signals a missing
link, which is a release blocker until the link is added.

| Req | Design section | Tasks |
|---|---|---|
| 1 (Architecture) | "Architecture", "Service Boundaries" | 1, 2A, 4, 5–12 |
| 2 (API & async) | "Async API and Operations" | 2, 13, 16 |
| 3 (Kafka) | "Messaging and Event Governance" | 2, 4, 13, 15 |
| 4 (Saga) | "Saga Designs" | 13, 14, 18 |
| 5 (Identity) | "Domain Preservation" (Member), "Authorization" | 5, 16 |
| 6 (Access) | "Authorization", "Service Boundaries" | 6, 16 |
| 7 (Domains) | "Service Boundaries", "Domain Preservation" | 7–12 |
| 8 (Transfer) | "Saga Designs / Import-Restore" | 12, 14, 18 |
| 9 (Migration) | "Migration and Cutover" | 14, 15, 18, 19 |
| 10 (Platform) | "Managed Platform", "Build and Release" | 3, 4, 17, 18 |
| 11 (Baseline) | "Technology Stack", "Project Architecture" | 2A, 4 |

## 1.4 Contradiction and unsupported-evidence scans

A small repository scanner lives at
`tooling/architecture-scan/`. It enforces:

1. **One target architecture** — fails if a non-archived file asserts a
   modular-monolith or single-DB deployment.
2. **Database-per-service** — fails if any `services/*/db/migration/**`
   references a table owned by another service.
3. **Mandatory Kafka** — fails if `services/*/pom.xml` lacks
   `spring-kafka` and the service produces domain events.
4. **Local authorization projections** — fails if a service consumes a
   synchronous RBAC endpoint in a non-emergency path.
5. **Async 202 contract** — fails if a controller method annotated with a
   cross-service mutation HTTP verb returns `200` or `ResponseEntity.ok`
   instead of `202 Accepted`.
6. **No foreign keys across services** — fails if a Flyway migration
   declares `REFERENCES services_*.public.*` or uses opaque IDs that are
   typed as the source-service aggregate.
7. **No synchronous call cycles** — fails if a service-to-service gRPC
   client is reachable transitively from itself.

The scan is wired into CI for every pull request.

## Acceptance

- Reviewers see one effective architecture document set (requirements,
  design, tasks, ADRs) in `.kiro/specs/spring-boot-backend-migration/`.
- The architecture-scan tool runs in CI and rejects violations.
- All traceability cells in §1.3 are populated.
