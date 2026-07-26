# ADR-002: Java 25 LTS, Spring Boot 4.1.0, MySQL 8.4 LTS

Status: Accepted · 2026-07 · Requirements: 14, 17, 18

## Context
The migration needs a long-support runtime, first-class virtual threads, and a relational
authority with PITR. Versions must be pinned so parity testing is reproducible.

## Decision
Pin Java 25 (LTS, virtual threads on by default for MVC), Spring Boot 4.1.0 BOM (manages
Jakarta/Framework versions), MySQL 8.4 LTS everywhere (local Docker, Testcontainers,
managed staging/production — identical minor).

## Alternatives
- Java 21 — rejected: 25 is the current LTS at adoption time; no legacy constraints.
- Spring Boot 3.x — rejected: 4.1 is the supported line for new work; avoids a forced
  upgrade mid-migration.
- PostgreSQL — rejected: team/ops standardize on managed MySQL; requirements name 8.4.
- MySQL 9 innovation — rejected: not LTS; PITR/ops maturity favors 8.4.

## Consequences
Toolchain enforced via Maven Toolchains + `maven-enforcer`; Testcontainers uses
`mysql:8.4` exactly; upgrade cadence follows LTS patch releases only during migration.

## Failure modes
Library lacking Boot-4 support → dependency convergence check fails in CI before merge.
Managed provider forces a MySQL patch → staging soak before production (same image tag in
Testcontainers).

## Rollback
Boot patch-version rollback is a pom change; MySQL patch rollback via managed provider
snapshot + PITR (RPO ≤ 5 min, see NFR doc).

## Single writer
Unaffected; version pinning has no data-ownership impact.
