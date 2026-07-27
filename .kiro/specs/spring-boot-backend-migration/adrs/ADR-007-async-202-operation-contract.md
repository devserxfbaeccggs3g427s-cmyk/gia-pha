# ADR-007: Asynchronous 202 Operation Contract

- **Status:** Accepted
- **Date:** 2026-07-27

## Context

Cross-service mutations cannot truthfully return synchronous success before Saga participants converge. Preserving the legacy synchronous contract would create false success, hidden partial failure, or request-time distributed coupling.

## Decision

A mutation that starts cross-service work returns `202 Accepted` with `operationId`, initial status `PENDING`, and `statusUrl`. Clients poll `GET /api/v2/operations/{operationId}`; a later SSE channel may supplement but not replace polling. The operation resource exposes authorized durable states and stable errors.

This is an intentional breaking change. The Gateway keeps public route continuity but does not preserve incompatible synchronous-success semantics. Idempotency keys bind retries to the existing operation. `SUCCEEDED` means required participants reached the target revision/epoch.

## Consequences

Next.js/PWA mutation UX and tests must change together with backend rollout. Completion latency has a separate SLO from request acceptance. Operations require retention, authorization, observability, cancellation/operator policy, and cleanup.

## Verification

OpenAPI, frontend contract, offline retry, authorization, state-transition, timeout, and polling tests must pass. Repository/document scans must find no promise of synchronous completion for cross-service mutations.
