# ADR-104: Clock policy

> Status: **Accepted** (per design.md §Clock policy).
> Phase: 0.1 (initial direction); implemented in `service-common`.

## Decision

- All services use UTC `Instant` everywhere; never `ZonedDateTime` in
  domain code.
- The `vn.giapha.platform.kernel` (already in `backend/platform-kernel`)
  is the only source of "now" in domain code.
- Saga ordering uses logical timestamps derived from `Instant` + a
  per-aggregate monotonic counter. Cross-service ordering is best-effort
  and reconciled.

## Rationale

- `Instant` is unambiguous across time zones and serialises cleanly.
- Single `Clock` bean is the standard Java pattern for testable time.

## Consequences

- `backend/platform-kernel.Clock` is reused; no new lib needed.
- Reconciliation jobs accept per-aggregate counter gaps as recoverable
  drift.
