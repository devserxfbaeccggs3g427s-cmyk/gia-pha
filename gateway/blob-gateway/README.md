# Blob Control Gateway

A standalone Vercel Function project that is the **only** holder of
`BLOB_READ_WRITE_TOKEN` (ADR-006, Requirement 7). It issues short-lived,
exact-path, single-operation signed URLs and performs controlled control-plane
operations (list, copy/promote) on behalf of the Spring backend. Bytes never
transit this function — browsers and Spring exchange payloads directly with
Vercel Blob using the signed URLs, so a 10 MiB upload is unaffected by the
4.5 MB Function body limit.

```
Spring / Next (service-signed HTTPS)          Browser
        │                                        │ PUT/GET bytes via signed URL
        ▼                                        ▼
┌─ blob-gateway ─────────────┐          ┌─ Vercel Blob (private) ─┐
│ /api/v1/signed-urls        │  control │  quarantine/…           │
│ /api/v1/list               │ ───────► │  media/…  artifacts/…   │
│ /api/v1/copy               │          │  backups/… archive/…    │
│ /api/v1/upload-callback ◄──┼──────────┤  (upload-completed)     │
└────────────────────────────┘          └─────────────────────────┘
```

## Endpoint contract (v1 — frozen; breaking changes require `/api/v2`)

All `/api/v1/*` endpoints are `POST`, accept `application/json`, require
[service authentication](#service-authentication), and answer with the
backend envelope family:

- Success: `{ "ok": true, "data": … }`
- Failure: `{ "ok": false, "error": { "code", "message" } }` — codes are
  frozen: `UNAUTHORIZED` 401, `FORBIDDEN` 403, `VALIDATION_ERROR` 400,
  `NOT_FOUND` 404, `CONFLICT` 409, `RATE_LIMITED` 429, `UPSTREAM_ERROR` 502,
  `INTERNAL` 500. Every response carries an `x-request-id` header.

### `POST /api/v1/signed-urls`

Issues one capability: exact pathname, exactly one operation, bounded expiry.

Request:

| Field | Type | Notes |
| --- | --- | --- |
| `operation` | `"get" \| "head" \| "put" \| "delete"` | required |
| `pathname` | string | required; normalized relative path inside the prefix allowlist |
| `ttlSeconds` | int | optional; clamped to `[10, GATEWAY_MAX_URL_TTL_SECONDS]` (default = max) |
| `contentType` | string | **put only, required**; must be in the content-type allowlist |
| `maxSizeBytes` | int | put only; may narrow `GATEWAY_MAX_PUT_BYTES`, never widen it |
| `callbackPayload` | string ≤2048 | put only; opaque value (upload-intent id) echoed in the completion callback |
| `useCache` | boolean | get only |
| `ifMatch` | string ≤256 | delete only; conditional delete |

Response `data`: `{ url, pathname, operation, expiresAt, [allowedContentTypes], [maximumSizeInBytes] }`.

Guarantees baked into every PUT capability: `allowOverwrite: false`,
`addRandomSuffix: false` (server-generated exact paths), single content type,
size ceiling, and the upload-completed callback registration. A capability can
never authorize another pathname or operation: the delegation token itself is
scoped to one pathname + one operation before the URL is presigned.

### `POST /api/v1/list`

`{ prefix, limit? (1..1000), cursor? }` → `{ blobs: [{ pathname, size,
uploadedAt }], hasMore, cursor }`. Prefixes obey the same allowlist as
pathnames. Store URLs are never returned — listing output cannot be turned
into access.

### `POST /api/v1/copy`

`{ fromPathname, toPathname }` → `{ pathname, contentType }`. Used for
quarantine → final promotion and archive replication. Both paths are
allowlist-checked; the destination is never overwritten (races surface as
`CONFLICT`).

### `POST /api/v1/upload-callback`

Called by Vercel Blob (not by services). The request signature is verified
against `BLOB_WEBHOOK_PUBLIC_KEY` before anything happens; the verified event
`{ pathname, tokenPayload, completedAt }` is then forwarded to
`GATEWAY_SPRING_CALLBACK_URL`, HMAC-signed with `GATEWAY_NOTIFY_KEYS[0]`
(issuer `giapha-blob-gateway`, audience `giapha-spring`). A non-2xx answer
from Spring fails the handler so Blob retries the notification. Spring treats
the event as a hint only — it re-verifies the object before promoting it.

### `GET /api/health`

Unauthenticated liveness probe: `{ "ok": true, "data": { "status": "UP" } }`.

## Service authentication

Every `/api/v1/*` request is signed with HMAC-SHA256 over the canonical
string (`\n`-joined):

```
giapha-v1 
 METHOD 
 /request/path 
 timestamp-ms 
 nonce 
 sha256hex(body) 
 issuer 
 audience
```

carried in the headers `x-giapha-timestamp` (±5 min skew), `x-giapha-nonce`
(per-instance replay cache), `x-giapha-issuer` (must be in
`GATEWAY_EXPECTED_ISSUERS`) and `x-giapha-signature` (lowercase hex,
timing-safe compare). All keys in `GATEWAY_SERVICE_KEYS` verify, so rotation
overlaps without downtime. An unconfigured gateway fails closed.

## Environment variables

| Variable | Required | Default | Purpose |
| --- | --- | --- | --- |
| `BLOB_READ_WRITE_TOKEN` | yes (Vercel-provided) | — | Blob store credential; exists **only** here |
| `BLOB_WEBHOOK_PUBLIC_KEY` | yes (Vercel-provided) | — | Verifies upload-completed callbacks |
| `GATEWAY_SERVICE_KEYS` | yes | — | Comma-separated inbound HMAC keys (all verify) |
| `GATEWAY_EXPECTED_ISSUERS` | no | `giapha-spring,giapha-next` | Allowed caller identities |
| `GATEWAY_AUDIENCE` | no | `giapha-blob-gateway` | Audience bound into every signature |
| `GATEWAY_PUBLIC_URL` | yes for uploads | — | This deployment's base URL, used to register upload callbacks |
| `GATEWAY_SPRING_CALLBACK_URL` | yes for uploads | — | Spring endpoint receiving forwarded completion events |
| `GATEWAY_NOTIFY_KEYS` | yes for uploads | — | Outbound HMAC keys; index 0 signs, all verify on the Spring side |
| `GATEWAY_ALLOWED_PATH_PREFIXES` | no | `quarantine/,media/,artifacts/,backups/,archive/` | Storage-layout allowlist |
| `GATEWAY_ALLOWED_CONTENT_TYPES` | no | `image/jpeg,image/png,image/webp,application/pdf` | PUT content-type allowlist (legacy media policy) |
| `GATEWAY_MAX_PUT_BYTES` | no | `10485760` | Hard upload ceiling (legacy 10 MiB cap) |
| `GATEWAY_MAX_URL_TTL_SECONDS` | no | `300` | Hard cap on signed-URL lifetime |

Secrets never appear in code, logs or responses. Logs are structured JSON with
`requestId`, `route`, `issuer`, `outcome`, `status`, `durationMs` and a
`pathHash` (16-hex SHA-256 prefix) instead of pathnames; signed URLs and
tokens never reach the logging module.

## Service-key rotation (no-downtime)

1. Generate a new key; append it to `GATEWAY_SERVICE_KEYS` (gateway now
   verifies old **and** new) and redeploy/update env.
2. Add the new key to the Spring/Next configuration
   (`giapha.blob-gateway.secrets`) and move it to index 0 so callers start
   signing with it.
3. After the overlap window (≥ max caller restart lag + 5 min skew), remove
   the old key from `GATEWAY_SERVICE_KEYS`.
4. `GATEWAY_NOTIFY_KEYS` rotates the same way in the opposite direction:
   add the new key to Spring's accepted set first, then promote it to index 0
   here, then retire the old one.

`BLOB_READ_WRITE_TOKEN` rotation is performed in the Vercel dashboard; the
gateway reads it per-request, so a redeploy with the new token completes the
swap.

## Deployment & rollback

- Deploy as its own Vercel project rooted at `gateway/blob-gateway/`
  (`framework: null`, functions under `api/`). Deployments are immutable;
  promotion happens by alias.
- Gate alias promotion on `GET /api/health` plus the staging contract tests.
- Rollback = point the alias back at the previous immutable deployment; no
  state lives in the function (the nonce cache is best-effort per instance).
- Spring pins the contract via the `/api/v1` prefix; incompatible changes ship
  as `/api/v2` alongside v1 (Task 13.6 versioning rule).

## Verification status

Structural guarantees (path/operation scoping, no-overwrite, TTL/size caps,
fail-closed auth, log hygiene) are implemented as described above. Staging
contract tests — including the 10 MiB browser upload proving bytes bypass the
Function body boundary and negative tests that a capability cannot authorize
another path/operation — are deferred with the rest of the test work per the
current migration instruction, and belong to the private staging checklist
before first production use.
