# Architecture Decision Records

ADRs for the Spring Boot backend migration (Task 5). IDs match the table in
`.kiro/specs/spring-boot-backend-migration/design.md`.

| ID | Decision | File |
|---|---|---|
| ADR-001 | Modular monolith, not microservices | [ADR-001](./ADR-001-modular-monolith.md) |
| ADR-002 | Java 25 LTS, Spring Boot 4.1.0, MySQL 8.4 LTS | [ADR-002](./ADR-002-java25-boot41-mysql84.md) |
| ADR-003 | Hexagonal Architecture per module | [ADR-003](./ADR-003-hexagonal-architecture.md) |
| ADR-004 | MySQL is the only structured-data target authority | [ADR-004](./ADR-004-mysql-single-authority.md) |
| ADR-005 | Vercel Blob stores only private binary/artifact objects | [ADR-005](./ADR-005-blob-binary-only.md) |
| ADR-006 | Official JS control gateway plus exact-path signed data plane | [ADR-006](./ADR-006-blob-control-gateway.md) |
| ADR-007 | Spring MVC, Spring Data JDBC and explicit SQL projections | [ADR-007](./ADR-007-mvc-data-jdbc.md) |
| ADR-008 | Outbox, upload intent and cleanup instead of distributed transaction | [ADR-008](./ADR-008-outbox-upload-intent.md) |
| ADR-009 | NextAuth bridge followed by global identity single-writer cutover | [ADR-009](./ADR-009-nextauth-bridge-identity-cutover.md) |
| ADR-010 | Legacy adapter plus versioned V2 API | [ADR-010](./ADR-010-compat-plus-v2-api.md) |
| ADR-011 | MySQL search and Caffeine first; add infrastructure by evidence | [ADR-011](./ADR-011-mysql-search-caffeine.md) |
| ADR-012 | Infrastructure DR is separate from user-level snapshots | [ADR-012](./ADR-012-dr-vs-user-snapshots.md) |
| ADR-013 | Per-tree cutover with no prolonged dual writes | [ADR-013](./ADR-013-per-tree-cutover.md) |
| ADR-014 | Strict allowlisted public share DTO | [ADR-014](./ADR-014-public-share-allowlist-dto.md) |
| ADR-015 | Tree revision drives ETag and cache consistency | [ADR-015](./ADR-015-tree-revision-etag.md) |

Every ADR records alternatives, rationale, consequences, failure modes, rollback and the
identifiable single writer for the states it governs (Task 5 Definition of Done).
