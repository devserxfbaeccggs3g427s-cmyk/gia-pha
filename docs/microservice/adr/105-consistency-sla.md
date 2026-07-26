# ADR-105: Eventual-consistency SLA

> Status: **Accepted** (per design.md §Consistency SLA).
> Phase: 0.1 (initial direction); enforced in Phase 6.2 reconciliation.

## Decision

| Invariant | SLA | Mechanism |
|---|---|---|
| Identity single-writer | Strict | Local MySQL row in `identity-service`. |
| Member→Media link tree scope | Eventually consistent ≤ 30 s | Event + projection. |
| Tree revision monotonic | Eventually consistent across services | `TreeRevisionIncremented` event. |
| Share link revocation | Immediate | Local write in `sharing-service`. |
| Audit log completeness | Eventually consistent; bounded lag | Audit fan-in + reconciliation. |
| Generated artifact freshness | Eventually consistent | Job status row in `transfer-service`. |

## Consequences

- Reconciliation jobs (Phase 6.2) report drift per SLA.
- The benchmark (Phase 7.1) measures queue age and outbox lag to
  validate against the SLA.
