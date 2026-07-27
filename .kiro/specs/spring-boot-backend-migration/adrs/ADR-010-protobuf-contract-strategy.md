# ADR-010: Protobuf Contract Strategy

- **Status:** Accepted
- **Date:** 2026-07-27

## Context

Kafka events and synchronous gRPC calls both require governed IDL, but they have different compatibility, ownership, publication, and consumption lifecycles. Combining all contracts into a shared domain package would couple services.

## Decision

Use Protobuf for Kafka event schemas and gRPC service contracts. Event contracts are versioned and published independently from gRPC service-contract artifacts. Schema Registry enforces backward compatibility for event schemas. Generated artifacts contain transport contracts only and never shared domain models.

## Consequences

Services may upgrade event and gRPC dependencies independently. Separate catalogs, ownership, release rules, and compatibility checks are required, and mapping between generated transport types and local domain types remains explicit.

## Verification

CI proves backward compatibility for event schemas, independent publication and consumption of event and gRPC artifacts, deterministic generation, versioned ownership, and rejection of forbidden PII, secrets, and shared domain source.
