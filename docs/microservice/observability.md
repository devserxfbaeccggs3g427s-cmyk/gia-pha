# Observability

> Phase 6.3 deliverable. Per Requirement 11 (observability).

## Tracing

- OpenTelemetry SDK in `service-common.observability`.
- Trace context propagates via `traceparent` header (W3C standard).
- Each service's logs include the trace id and request id via the
  logging pattern in `application.yml`.

## Metrics

Per-service p95/p99 latency, error rate, outbox lag, queue age, and
reconciliation drift count. Implemented as Micrometer counters +
timers and exposed via `/actuator/prometheus`.

## Dashboards

Grafana dashboards (provisioned from `infra/grafana/dashboards/`):

| File                          | Purpose |
|-------------------------------|---------|
| `services-overview.json`      | Per-service p95 latency + error rate |
| `outbox-lag.json`             | Outbox lag per service |
| `saga-duration.json`          | Saga completion time histogram |
| `cross-service-trace.json`    | End-to-end request trace waterfall |

## Alerts

Alert rules (in `infra/prometheus/alerts.yml`):

| Alert | Condition | Severity |
|---|---|---|
| `OutboxLagHigh` | outbox unpublished > 30 s for 2 min | warning |
| `SagaStuck` | saga in COMPENSATING > 5 min | critical |
| `ServiceDown` | actuator/health/liveness != 200 for 1 min | critical |
| `ReconciliationDrift` | drift count > 1% for 10 min | warning |

## Correlation

Every request carries `X-Request-Id`. The gateway assigns a UUID if
the header is missing. Outbox publishers stamp every event with the
same id. Logs include the id via SLF4J MDC.

## Replay

Reconstructing a saga's behaviour end-to-end:

1. Filter logs by `requestId=<id>` across services.
2. Open the matching trace in Jaeger / Tempo.
3. Cross-reference `saga_log` rows for state transitions.
