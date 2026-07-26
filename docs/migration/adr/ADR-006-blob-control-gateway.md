# ADR-006: Official JS control gateway plus exact-path signed data plane

Status: Accepted · 2026-07 · Requirements: 7.5–7.9, 15.3

## Context
Vercel Blob's official SDK is JavaScript; there is no supported Java SDK. Spring must
still authorize every binary operation, and browsers must upload 10 MiB files without
passing bytes through a 4.5 MB-limited Vercel Function body or through Spring.

## Decision
A minimal Vercel Function ("control gateway") using the latest stable `@vercel/blob`
issues **exact-path, single-operation, short-expiry signed URLs** (GET/HEAD/PUT/DELETE),
verifies upload callbacks with `BLOB_WEBHOOK_PUBLIC_KEY`, and offers authenticated
paginated list + controlled copy. Spring calls it with rotating service keys; bytes flow
browser/Spring ↔ Blob directly (data plane).

## Diagram
```
Spring ──service auth──▶ control gateway (Vercel Fn, @vercel/blob)
  │                                 │ signed URL {path,op,ct,≤5min}
  └────────── bytes ────────────────▼
     browser/Spring  ◀────────────▶  Vercel Blob (data plane)
```

## Alternatives
- Reverse-engineer Blob REST from Java — rejected: unsupported/unstable surface.
- Proxy all bytes through Spring — rejected: doubles egress, defeats direct upload,
  couples media latency to API pods.
- Public objects with unguessable URLs — rejected: violates private-data requirement.

## Consequences
One extra deployable (gateway) with its own contract tests; capability tokens are
non-transferable across path/operation; gateway is stateless so scaling is trivial.

## Failure modes
Gateway down → binary operations 503; structured API unaffected. Key leak → rotate
service key (dual-key window, Task 13.6); signed URLs expire ≤ 5 min anyway.
Callback forgery → rejected by `BLOB_WEBHOOK_PUBLIC_KEY` signature check.

## Rollback
Gateway versions are immutable Vercel deployments; roll back by aliasing the previous
deployment. Spring pins a gateway contract version header.

## Single writer
Only the gateway holds `BLOB_READ_WRITE_TOKEN`; Spring holds only service keys; browsers
hold only single-use capabilities.
