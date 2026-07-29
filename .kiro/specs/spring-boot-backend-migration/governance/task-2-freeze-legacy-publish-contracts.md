# Task 2 — Freeze Legacy Behavior and Publish New Contracts

This task freezes the legacy Next.js route surface as **migration input** and
publishes the new V2 contracts. The legacy app is **not** the target; the
contracts in this directory are.

## 2.1 Legacy inventory

The legacy surface is inventoried at
`frontend-legacy-inventory.yaml`. It records every route, frontend caller,
domain rule, status code, payload, header, cookie, and binary behaviour
that the V2 contracts must replace or explicitly drop.

## 2.2 Golden fixtures

Sanitised golden and property fixtures live under `contracts/fixtures/`.
They cover:

- Identity: BCrypt hashes, OAuth accounts, registration limits
- Genealogy: members, relationships, generations, adoption
- Recurrence: events, recurrence, leap-day behaviour
- Vietnamese search: diacritics-insensitive matching, autocomplete
- Redaction: PII removal, audit redaction
- Import: GEDCOM, JSON, CSV manifest samples
- Export: GEDCOM, JSON, CSV expected output samples

## 2.3 V2 OpenAPI

The Gateway exposes a single versioned OpenAPI document at
`contracts/api/v2/openapi.yaml`. Every cross-service mutation returns the
`202 Accepted` envelope below:

```yaml
schema:
  AsyncOperation:
    type: object
    required: [operationId, status, statusUrl]
    properties:
      operationId: { type: string, format: uuid }
      status: { type: string, enum: [PENDING, RUNNING] }
      statusUrl: { type: string }
      watermarks: { type: object, additionalProperties: { type: string } }
```

The V2 contract adds:

- `202 Accepted` envelope for every cross-service mutation
- `GET /api/v2/operations/{operationId}` for polling
- ETag / `If-Match` on every read
- `revision` / `watermark` query parameter
- `Idempotency-Key` request header on every retriable command
- `409 Conflict` on idempotency-key reuse with a different payload hash
- Stable error envelope (`error.code`, `error.message`, `error.traceId`)

## 2.4 Frontend / PWA migration contract

The PWA must:

1. Treat any `2xx` other than `200` on a cross-service mutation as
   **acceptance**, not completion.
2. Poll `GET /api/v2/operations/{operationId}` until `status` is terminal
   (`SUCCEEDED`, `FAILED`, `COMPENSATED`, `MANUAL_REVIEW`).
3. Generate a stable `Idempotency-Key` per logical user action; never
   auto-retry the same command without rotating the key.
4. Clear private caches and offline queues on identity change
   (`identity:changed` event).
5. Never assume cross-service work is complete when the mutation response
   is `2xx`.

## 2.5 Event catalog

The Kafka event catalog is published at
`contracts/events/catalog.yaml`. Every entry records:

- `name` and `version`
- Owning service
- Topic and partition key
- Producer/consumer compatibility level
- Retention class
- PII classification (`NONE`, `LOW`, `MEDIUM`, `HIGH`)

## Acceptance

- Every frontend operation maps to a V2 OpenAPI operation.
- Every cross-service mutation returns a `202 Accepted` envelope.
- Event schemas pass the backward-compatibility gate in
  `platform/ci/schema-compat.sh`.
- Legacy exceptions (synchronous-success) are explicitly removed.
