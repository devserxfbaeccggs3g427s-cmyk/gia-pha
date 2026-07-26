# ADR-007: Spring MVC, Spring Data JDBC and explicit SQL projections

Status: Accepted · 2026-07 · Requirements: 14, 17

## Context
The workload is modest (NFR doc §1) but correctness-critical: tree-scoped queries,
canonical relationship rows, deterministic ordering for parity. ORMs with dirty checking
and lazy loading make byte-level payload parity and query-plan review harder.

## Decision
Spring MVC (virtual threads, blocking simplicity) + Spring Data JDBC for aggregates +
`JdbcClient` with hand-written parameterized SQL for every read projection. No JPA, no
entity graph magic; each query is visible, reviewable and explainable (`EXPLAIN` review,
Task 10.5).

## Alternatives
- WebFlux — rejected: no back-pressure need at ≤ 100 RPS; virtual threads give
  concurrency without the reactive tax.
- JPA/Hibernate — rejected: implicit flush/dirty-checking risks non-parity writes;
  N+1 mitigation obscures plans.
- jOOQ — considered; deferred: adds codegen pipeline; `JdbcClient` suffices for the
  frozen query set, revisit for V2 analytics.

## Consequences
More explicit SQL to maintain, but 1:1 mapping to the data dictionary (Task 9.6);
optimistic locking via explicit `version` columns; batch writes via named-param batches.

## Failure modes
Hand-written SQL drift from schema → Flyway-migrated Testcontainers round-trip tests per
repository. Accidental global scan → repository API takes `treeId` as mandatory scope
(Task 10.3) + ArchUnit naming rule.

## Rollback
Library-local decision; a module could adopt jOOQ behind its repository port without
touching domain code.

## Single writer
Repositories are package-private to their module's `adapter.out.jdbc`; cross-module table
writes cannot compile.
