# ADR-010: Legacy adapter plus versioned V2 API

Status: Accepted · 2026-07 · Requirements: 1, 20

## Context
The frontend/PWA depends on the frozen legacy contract (mixed envelopes, DEF-02/03/11)
which is unpleasant to keep forever, but changing it during migration would break parity
testing.

## Decision
Two inbound HTTP adapters over one application core:
- **Compatibility_API** at `/api/**`: byte-parity with the OpenAPI baseline, validated by
  golden fixtures and CI OpenAPI diff (Task 11.4). Frozen — no new features.
- **V2_API** at `/api/v2/**`: uniform envelope, pagination, idempotency keys, problem
  details, token auth. All new features land here only.

## Alternatives
- Evolve the legacy surface in place — rejected: destroys the parity oracle mid-migration.
- Only compatibility, defer V2 — rejected: forces new features into the frozen quirks
  and delays decommission incentives.
- GraphQL for V2 — rejected: REST matches existing client stack and caching model.

## Consequences
Per-operation mapping tables live in the compatibility adapter only; the application core
knows nothing about envelopes; V2 gets an independently generated OpenAPI document.

## Failure modes
Behavior drift between adapters → both are thin mappers over the same use cases; contract
tests run the golden fixtures against Compatibility and equivalent semantic tests against
V2. Accidental feature added to compat → CI OpenAPI diff against the frozen baseline
fails.

## Rollback
V2 endpoints are additive; disabling V2 routes has no effect on the migration.

## Single writer
Both adapters call the same use cases; write ownership is unchanged (ADR-004/009/013).
