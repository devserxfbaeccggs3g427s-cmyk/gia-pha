# Task 11 — Implement Sharing Service

## Scope

Sharing owns hashed link creation/list/revocation/expiry, allowlisted
public projections, and token-plus-media-ID access
(`requirements.md:109`, `design.md:72,167`). Revocation MUST take
effect synchronously in this service: no public read may succeed
once a link is revoked (`requirements.md:5`, `design.md:167`).

## Domain ownership

| Aggregate | Authoritative writes |
|---|---|
| `share_link` | Sharing service only. Stores token hash, expiry, revocation timestamp, scope, role, target. |
| `share_allowlisted_projection` | Sharing service only. One row per `(treeId, scope, targetId)`. JSON is the canonical public projection; PII outside the allowlist is rejected at write time. |
| `share_watermark` | Per-tree advanced atomically with each projection rebuild. |

## Behaviour

### Token handling (11.1)

- Tokens are random 256-bit secrets; only the SHA-256 hash is stored.
- The plaintext token is returned exactly once to the caller on link
  creation. It is never logged, never persisted, never emitted in
  events.
- A migration path accepts legacy tokens issued by the prior system;
  they are hashed on import and the legacy source is recorded for
  reconciliation.

### Allowlisted projections (11.2)

- The service consumes `tree.events.v1`, `member.events.v1`,
  `media.events.v1`, and `relationship.events.v1` into the local
  `share_*_projection` tables (one per source domain).
- The public projection is a strict allowlist; consumers that ask
  for member or media projection never see private fields
  (`userId`, `email`, raw Blob URLs, owner details).
- Revocation events short-circuit the rebuild for the affected scope
  and force the public projection to clear the entry.

### Token-plus-media-ID access (11.3)

- Public endpoints accept `token` + `mediaId` only.
- The service validates the token hash, ensures the scope contains
  the media, and returns a one-shot signed `Blob` capability
  minted by the media service via the gRPC contract.
- Arbitrary paths are never accepted. The response never carries
  the raw Blob URL.

### Verification (11.4)

- Synchronous revocation: `revoke()` writes the row in the same
  transaction that publishes `ShareLinkRevoked`; the public
  projection read path filters out revoked links before answering.
- Forbidden fields: the projection writer rejects any unknown key
  in the allowlist map.
- Replay/rebuild: watermark-driven rebuild replays source events
  after `share_watermark`.
- Unknown/expired tokens: `404` with stable error code `share.unknown`.

## Events

- Topics: `sharing.events.v1`.
- Partition key: `treeId`.
- Event types: `ShareLinkCreated`, `ShareLinkRevoked`,
  `ShareProjectionRebuilt`.
- Payload contains: `treeId`, `shareId`, `scope`, `role`, `revision`,
  `occurredAt`. Never the token, never the raw URL.

## Public endpoints

- `POST /api/v2/sharing/links` — create a hashed link; returns
  `{ shareId, token, expiresAt }`. Token is one-shot.
- `GET /api/v2/sharing/links?treeId=...` — list links (auth required).
- `DELETE /api/v2/sharing/links/{shareId}` — synchronous revocation.
- `GET /api/v2/public/sharing/{token}/media/{mediaId}` — public,
  allowlisted projection lookup. Revoked tokens never succeed.

## Acceptance

- `share_link`, `share_allowlisted_projection`, `share_watermark`,
  `outbox_record`, `inbox_record`, `idempotency_record`,
  `operation_audit` exist.
- `CreateShareLinkUseCase`, `RevokeShareLinkUseCase`,
  `ResolvePublicProjectionUseCase`, and the `MigrationController`
  exist.
- Public projection reader strips any field not in the allowlist.
- Revocation takes effect before the next read; replay and rebuild
  use the watermark.
