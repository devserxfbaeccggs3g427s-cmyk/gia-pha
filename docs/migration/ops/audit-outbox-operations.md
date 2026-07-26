# Audit, Outbox and Durable Worker Operations

Runbook for the `audit-operations` module (Task 12, Requirement 13). Covers the two audit
trails, the transactional outbox, worker behaviour, retention, metrics/alerts and the
operator API. No procedure in this document requires direct SQL.

## 1. Audit trails

Two append-only trails with **separate schemas and separate retention** (Req 13.3):

| Trail | Table | Written | Retention default |
| --- | --- | --- | --- |
| Business history | `audit_logs` | `Propagation.MANDATORY` — inside the business transaction; a rollback erases the row (Req 13.1/13.4) | `giapha.workers.business-audit-retention=PT0S` → **kept forever** (legacy `ChangeLog` parity) |
| Security events | `security_audit_logs` | `Propagation.REQUIRES_NEW` — survives a business rollback so failed/blocked attempts are still recorded | `giapha.workers.security-audit-retention=P400D` |

`MANDATORY` doubles as an enforcement gate: calling the audit or outbox append outside a
transaction fails fast instead of silently writing an unanchored row.

### Redaction (Req 13.2)

`AuditRedactor` applies three layers before anything is persisted:

1. **Allowlist per entity type** — only fields frozen from the legacy `src/data/types.ts`
   shapes survive; URLs that may embed Blob capabilities (`avatarUrl`, `blobUrl`,
   `thumbnailUrl`, `contentUrl`) are excluded by construction.
2. **Forbidden-key guard** — any key matching `password|hash|token|secret|cookie|session|
   credential|capability|signature|authorization|apikey` is dropped even if a future
   allowlist mistake would admit it.
3. **Value scrub** — JWT-shaped strings, bearer values, credentialed URLs
   (`?token=`, `?sig=`, `X-Amz-*`, SAS params), data URIs and raw bytes become `[REDACTED]`.

Security-trail identifiers are stored hashed (`email_hash`, `ip_hash` = SHA-256), never raw.
The same `sanitizePayload` pass guards every outbox payload.

## 2. Outbox lifecycle

```
enqueue (MANDATORY, same txn as the mutation — Req 13.7)
   │
PENDING ──claim (FOR UPDATE SKIP LOCKED + lease)──▶ IN_PROGRESS
   ▲                                                   │
   │  lease expiry → reclaimed                         ├─ handler ok ──▶ COMPLETED ─▶ purged after
   │                                                   │                             completed-outbox-retention (P7D)
   └── FAILED ◀── handler error, attempts < max ───────┘
        │              (available_at += backoff with full jitter)
        └── attempts ≥ max, or operator cancel ──▶ DEAD (replayable)
```

- Delivery is **at-least-once**; every `OutboxEventHandler` must be idempotent (Req 13.8).
  A crash between handler success and the completion update redelivers after lease expiry.
- Backoff: `min(backoffMax, backoffBase << (attempts-1))` with full jitter, 1 s floor.
- `leased_by` records `hostname#suffix` of the claiming worker for attribution.
- An event whose type has **no registered handler** retries and eventually dead-letters —
  expected while the owning module is not yet deployed; replay it after deployment.

## 3. Workers

| Worker | Schedule | Behaviour |
| --- | --- | --- |
| `OutboxWorker` | `giapha.workers.outbox-poll-interval` (PT2S) | Reclaims expired leases, claims up to `outbox-batch-size` (50), drains back-to-back while the queue is hot. Safe to run any number of instances — SKIP LOCKED keeps claims disjoint. |
| `RetentionWorker` | `giapha.workers.retention-poll-interval` (PT1H) | Idempotent, row-capped sweeps (`retention-sweep-batch=500` per table per cycle): expired idempotency records, COMPLETED outbox rows, and audit trails per their retention. `PT0S` disables a sweep. |

Worker crash/restart needs no operator action: leases expire (`lease-duration=PT5M`) and
rows are reclaimed automatically. A cycle-level failure (e.g. DB outage) is logged and
retried on the next tick; the scheduled task never dies.

## 4. Metrics and alerts (Task 12.5)

Micrometer meters, refreshed once per relay cycle (scrapes never query the DB):

| Meter | Type | Alert guidance |
| --- | --- | --- |
| `giapha.outbox.ready` | gauge | Sustained growth ⇒ workers under-provisioned or a handler is failing. |
| `giapha.outbox.ready.age.seconds` | gauge | **Page at > 300 s** — events are not being drained. |
| `giapha.outbox.in.progress` | gauge | Should hover near 0 between cycles; a plateau ⇒ stuck handler holding leases. |
| `giapha.outbox.dead` | gauge | **Warn on any increase**; every DEAD event needs triage (inspect → fix → replay). |
| `giapha.outbox.completed` / `.retried` / `.dead.lettered` | counters | Retry-rate spike ⇒ inspect `last_error` of FAILED events. |

## 5. Operator API (Req 13.9)

All endpoints require the `OPS` authority (same bar as non-health Actuator endpoints) and
answer in the standard `{ok, data}` envelope.

| Endpoint | Purpose |
| --- | --- |
| `GET /api/ops/outbox?status=DEAD&page=1&pageSize=20` | List events; optional status filter (`PENDING`, `IN_PROGRESS`, `COMPLETED`, `FAILED`, `DEAD`). |
| `GET /api/ops/outbox/{id}` | Inspect one event: parsed payload, attempts, `last_error`, lease holder, timestamps. |
| `POST /api/ops/outbox/{id}/replay` | Requeue a COMPLETED/FAILED/DEAD event with a fresh attempt budget (replaying COMPLETED is legal — handlers are idempotent). Rejected with 409 for PENDING/IN_PROGRESS. |
| `POST /api/ops/outbox/{id}/cancel` | Dead-letter a PENDING/FAILED event so no worker runs it. Rejected for events under a live lease; cancelled events remain replayable. |

### Triage recipe for a DEAD event

1. `GET /api/ops/outbox?status=DEAD` → pick the event, read `lastError` and `payload`.
2. Fix the root cause (deploy the missing handler, repair downstream dependency, …).
3. `POST /api/ops/outbox/{id}/replay` and watch `giapha.outbox.dead` drop next cycle.

## 6. Configuration reference (`giapha.workers.*`)

| Key | Default | Meaning |
| --- | --- | --- |
| `outbox-poll-interval` | `PT2S` | Idle poll latency of the outbox worker. |
| `outbox-batch-size` | `50` | Events claimed per cycle (also the lease-reclaim cap). |
| `lease-duration` | `PT5M` | Claim lease; expired leases are reclaimed by any worker. |
| `max-attempts` | `10` | Attempt budget before dead-lettering. |
| `backoff-base` / `backoff-max` | `PT30S` / `PT1H` | Retry backoff curve (full jitter). |
| `business-audit-retention` | `PT0S` (disabled) | Purge horizon for `audit_logs`; keep disabled for legacy parity. |
| `security-audit-retention` | `P400D` | Purge horizon for `security_audit_logs`. |
| `completed-outbox-retention` | `P7D` | How long COMPLETED rows stay inspectable. |
| `retention-poll-interval` | `PT1H` | Retention sweep cadence. |
| `retention-sweep-batch` | `500` | Row cap per delete statement per cycle. |

All values bind through `GiaPhaProperties.Workers` (typed, validated at startup) and are
overridable via environment variables, e.g. `GIAPHA_WORKERS_OUTBOXBATCHSIZE=100`.
