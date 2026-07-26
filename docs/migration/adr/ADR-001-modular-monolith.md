# ADR-001: Modular monolith, not microservices

Status: Accepted · 2026-07 · Owners: backend team · Requirements: 1–20

## Context
One small team migrates a Next.js/Blob backend used by a single product. Domain modules
(identity, tree content, media, sharing, transfer, reporting, audit) are cohesive but the
operational budget is one deployable and one database.

## Decision
Build one Spring Boot deployable (`app-bootstrap`) composed of nine Maven modules with
enforced compile-time boundaries (ArchUnit + module dependency rules). Modules communicate
only through published Java ports; no module reads another module's tables.

## Alternatives
- **Microservices** — rejected: operational cost (N deployments, distributed tracing,
  network partitions) with zero scaling evidence; DoD demands single-writer clarity, which
  distribution complicates.
- **Unstructured monolith** — rejected: boundary erosion would block a later extraction
  and makes the strangler migration ledger ambiguous.

## Diagram
```
app-bootstrap ──▶ platform-kernel
     ├─▶ identity-access   ├─▶ tree-content    ├─▶ binary-storage
     ├─▶ sharing           ├─▶ transfer        ├─▶ reporting-search
     └─▶ audit-operations  (all depend only on platform-kernel ports)
```

## Consequences
Single CI/CD pipeline and transaction manager; module boundaries testable in CI;
extraction to a service later is a packaging change, not a rewrite.

## Failure modes
Boundary leaks via shared entities → ArchUnit failure blocks merge. Hot module starves
others → thread-pool bulkheads per worker group (Task 12).

## Rollback
None needed at runtime; the decision is reversible per module by extracting it behind its
existing port.

## Single writer
Exactly one deployable writes MySQL; per-tree/identity write ownership during migration is
recorded in the migration ledger (ADR-009, ADR-013).
