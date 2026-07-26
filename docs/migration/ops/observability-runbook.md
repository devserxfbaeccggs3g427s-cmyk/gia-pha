# Observability and On-Call Runbook

Spec: spring-boot-backend-migration — Task 43 (Req 17).

## Logs / metrics / traces

- **Logs:** structured JSON, correlation id (`X-Request-Id`), redacted of
  PII (email-hash, IP-hash). Log level policy:
  - `INFO` for every state-change (auth, mutation, replication).
  - `WARN` for any retryable failure (deadlocks, breaker open, transient 5xx).
  - `ERROR` for permanent failures (dead-letter, scanner outage, drift).
  - Never `DEBUG` in production.
- **Metrics:** Micrometer + Prometheus scrape; SLO dashboards in Grafana.
- **Traces:** OpenTelemetry over OTLP, propagated through the bridge
  token + cookie. Sampling: 10% always, 100% on 5xx.

## SLO dashboards

| SLO | Burn alert |
| --- | --- |
| Auth latency p95 < 250 ms | 2% budget in 1h |
| API latency p95 < 400 ms | 2% budget in 1h |
| API 5xx rate < 0.1% | page |
| Bridge replay rejection < 1% | page |
| Identity migration reconciliation diff = 0 | page |
| Outbox age p95 < 30 s | page |
| Cleanup overdue (now - available_at > 24h) > 5 | page |
| Replication lag p95 < 6 h | page |
| Share public cache hit < 1% | warn |

## Stop conditions

- A production write reaches Blob JSON (`users.json`).
- Two identity writers attempt a row update simultaneously.
- A blocking reconciliation discrepancy opens.
- SLO breach sustained for > 30 minutes.
- Outbox age > 5 minutes for 3 consecutive polls.

## Runbooks (linked)

| Topic | File |
| --- | --- |
| Cutover / rollback | `docs/migration/ops/identity-cutover-runbook.md` |
| Bridge token | `docs/migration/ops/bridge-token-runbook.md` |
| Snapshot / restore | `docs/migration/ops/snapshot-restore.md` |
| Binary replication | `docs/migration/ops/binary-replication-runbook.md` |
| Erasure / legal hold | `docs/migration/ops/erasure-runbook.md` |
| Reconciliation | `docs/migration/ops/reconciliation-runbook.md` |
| Shadow read | `docs/migration/ops/shadow-read-runbook.md` |
| Canary / cohort gating | `docs/migration/ops/canary-runbook.md` |
