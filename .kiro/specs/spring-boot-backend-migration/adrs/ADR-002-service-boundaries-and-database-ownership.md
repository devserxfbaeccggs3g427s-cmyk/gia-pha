# ADR-002: Service Boundaries and Database Ownership

- **Status:** Accepted
- **Date:** 2026-07-27

## Context

Identity, access, genealogy graph, events, media, sharing, reporting, transfer, operations, and migration have different ownership, consistency, scaling, security, and lifecycle needs. A shared schema would preserve hidden coupling and distributed ownership ambiguity.

## Decision

Use the bounded contexts defined in `design.md`: Identity, Tree Access, Member, Relationship, Event, Media & Album, Sharing, Search & Reporting, Transfer, Audit & Operations, and Migration & Reconciliation. Each owns its MySQL 8.4 database/logical database, Flyway history, credentials, tables, and writes. Network policy prohibits access to another service database. Cross-service references are opaque external IDs and versions; there are no cross-service foreign keys or transactions.

Integrity across boundaries is maintained through validated commands, local projections, tombstones, aggregate versions, and anti-entropy reconciliation. MySQL is the structured source of truth for each owner; Vercel Blob contains binaries/artifacts only.

## Consequences

Some reads require projections and expose freshness watermarks. Service-specific loaders, reconciliation endpoints, deploys, backups, and restores are required. Shared platform libraries may contain infrastructure contracts but not business domain models.

## Verification

Credential/network tests and architecture tests must prove database isolation. Reconciliation must report dangling opaque references and projection gaps. Independent deployment and rollback must be rehearsed for each service.
