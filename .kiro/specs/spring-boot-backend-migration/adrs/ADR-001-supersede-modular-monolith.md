# ADR-001: Supersede the Modular Monolith

- **Status:** Accepted
- **Date:** 2026-07-27

## Context

The prior specification selected one Spring deployable and treated microservice decomposition and Kafka as out of scope. The authoritative migration plan instead requires direct adoption of independently deployable bounded contexts, database-per-service, mandatory Kafka, and managed Kubernetes. Keeping both decisions effective would make requirements and acceptance criteria contradictory.

## Decision

The Spring target is microservices from the start of migration. This ADR supersedes every prior modular-monolith decision, including the former ADR-001 and descriptions of one shared tree-content transaction/database. No intermediate modular-monolith production architecture is planned.

## Consequences

Cross-domain atomicity is replaced by local transactions, Saga orchestration, epochs, tombstones, and reconciliation. Operational complexity increases and the managed platform, service template, contract governance, observability, and fault testing become prerequisites. Legacy domain behavior remains a parity goal only where it does not conflict with approved distributed contracts.

## Verification

Architecture/document scans must find one target architecture. CI architecture tests must reject shared business models, cross-service database access, and synchronous service cycles.
