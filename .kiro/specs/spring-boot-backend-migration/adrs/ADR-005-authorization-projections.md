# ADR-005: Local Authorization Projections

- **Status:** Accepted
- **Date:** 2026-07-27

## Context

Tree Access owns memberships and RBAC, but synchronous authorization calls on every domain request would create latency, availability coupling, and long call chains. Eventual projections can lag, so fail-open cache behavior is unsafe.

## Decision

Tree Access Service is authoritative for tree ownership, memberships, roles, and revision/epoch. Every domain service consumes ordered membership events into a local versioned authorization projection and uses it on the normal request path. Decisions bind principal, tree, permission, and projection version.

Unsafe mutations are denied when the projection is missing or stale beyond policy. Sensitive reads fail closed or use a deadline-bound emergency Tree Access lookup. Emergency lookup never fails open. Membership revocation receives prioritized propagation, monitoring, replay, and reconciliation.

## Consequences

Authorization data is duplicated but ownership remains singular. Temporary denial is preferred to unauthorized access during lag. Services must expose projection freshness and support rebuilds.

## Verification

Role-by-operation, IDOR/cross-tree, stale-role, missing-version, Kafka-delay/outage, immediate-revocation, emergency-timeout, replay, and reconciliation tests are mandatory. Alerts enforce the approved revocation and freshness objectives.
