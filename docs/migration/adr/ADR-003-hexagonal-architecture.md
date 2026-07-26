# ADR-003: Hexagonal Architecture per module

Status: Accepted · 2026-07 · Requirements: 17, 18

## Context
Domain rules (generation, normalization, recurrence) must be portable, unit-testable
against the golden corpus, and independent of MySQL/Blob/HTTP details that change during
the strangler migration.

## Decision
Every module is split into `domain` (pure Java, zero framework imports), `application`
(use cases, transactions), and `adapter` packages (`adapter.in.web`, `adapter.out.jdbc`,
`adapter.out.blob`, …). Dependencies point inward only; ArchUnit rules enforce this and
ban framework types from `domain`.

## Alternatives
- Layered-by-technology (controller/service/repo across all domains) — rejected: no
  seam for the compatibility-vs-V2 dual inbound adapters, weak parity testability.
- CQRS/event-sourcing — rejected: complexity unwarranted; the outbox (ADR-008) gives the
  needed async seam without event-sourced state.

## Diagram
```
        in.web (Compat_API)  in.web.v2 (V2_API)
                 \              /
                application (use cases, tx)
                        |
                     domain  ◀── golden-corpus tests
                 /              \
        out.jdbc (MySQL)   out.blob (gateway client)
```

## Consequences
Golden-corpus parity tests run on `domain` with no containers; both API generations share
one application core, so behavior cannot diverge between envelopes.

## Failure modes
Anemic domain with logic drifting to adapters → code review checklist + ArchUnit rule that
adapters may not depend on each other.

## Rollback
Package refactor only; no data impact.

## Single writer
`adapter.out.jdbc` is the only place SQL exists; a module's tables are written by exactly
that module's adapter.
