# Stabilization and DR Validation

Spec: spring-boot-backend-migration — Task 52 (Req 12, 16-17, 20.12).

## Monitoring period

After every tree cohort flip, observe for the approved window before
migrating the next cohort. The stabilization period focuses on:

- API latency p95 / p99 and error rate.
- Auth latency (credential login, bridge token exchange).
- Worker backlog (outbox, cleanup, replication).
- SLO burn alerts (Grafana, Prometheus).
- Support tickets / sentry errors.
- Customer escalations from CS.

## Query / worker tuning

Based on production evidence, adjust:

- Indexes via EXPLAIN (no unapproved scans).
- Connection pool sizes (HikariCP).
- Worker batch sizes.
- Outbox relay poll interval.
- Cleanup lease duration.
- Replication lag thresholds.

Every change requires an ADR-style note recorded in
`docs/migration/ops/tuning-log.md`.

## DR validation

| Drill | Cadence | Owner |
| --- | --- | --- |
| Application snapshot restore | weekly | platform |
| MySQL PITR | weekly | DBA |
| Binary archive restore | weekly | platform |
| Identity rollback | monthly | platform + DBA |
| Bridge key rotation | monthly | security |

Every drill records evidence in `docs/migration/ops/dr-evidence/`.

## Closure criteria

- No unresolved Sev-1 / Sev-2 for 14 consecutive days.
- SLO breach budget stays under the approved burn.
- DR drills pass with no surprises.
- Business, security, and operations sign-off.
