# Frontend integration

This document captures how the Next.js BFF (BFF — "Backend for Frontend")
delegates every authenticated request to the Spring Cloud Gateway
(`services/gateway-service:8080`), which in turn routes to the
appropriate microservice.

> Before this work, the BFF read and wrote Vercel Blob JSON files
> directly (the legacy monolith did the same). After this work, the
> BFF is a thin adapter layer that only handles auth, RBAC, DTO
> mapping, and route-level error envelopes.

## Architecture

```
Next.js BFF (src/app/api/**)
   │
   ├── requireAuthenticatedUserId()  // NextAuth session check
   ├── requireTreePermission()      // gateway-backed RBAC
   ├── <service>.<Method>(...)
   │
   └─→ springFetch() via spring-client.ts
         │
         └─→ http://gateway-service:8080/api/<svc>/...
                │
                └─→ <svc>-service (8081..8091)
                       │
                       └─→ MySQL schema + outbox
```

## Components

| File | Purpose |
|---|---|
| `src/lib/api/spring-client.ts` | Generic HTTP client (fetch with timeout, cookie forwarding, AbortController) |
| `src/lib/api/spring-guard.ts` | `requireBridgeCookie()` reads the bridge cookie from Next.js request context |
| `src/lib/api/dto-mapper.ts` | Convert microservice responses → frontend types (FamilyTree, Member, …) |
| `src/lib/api/service-error.ts` | `ServiceError` exception used by BFF services |
| `src/lib/api/service-error-handler.ts` | Map exceptions → frozen error envelope (SpringError, ServiceError, AuthError, ZodError) |
| `src/lib/api/bridge-exchange.ts` | Warms the gateway's session cache after `/api/auth/bridge` |

## Service-to-gateway mapping

The gateway's `application.yml` exposes routes under `/api/<svc>/**`:

| Path prefix | Owner |
|---|---|
| `/api/identity/**` | identity-service |
| `/api/audit/**` | audit-service |
| `/api/trees/**`, `/api/tree/**` | tree-service |
| `/api/members/**` | members-service |
| `/api/relationships/**` | relationships-service |
| `/api/events/**` | events-service |
| `/api/media/**` | media-metadata-service |
| `/api/binary/**` | binary-storage-service |
| `/api/share/**`, `/api/sharing/**` | sharing-service |
| `/api/reports/**`, `/api/reporting/**` | reporting-service |
| `/api/transfer/**`, `/api/import/**`, `/api/export/**` | transfer-service |
| `/api/internal/**` | identity-service (session validation) |

## DTO mapping rules

| Frontend type | Microservice field | Notes |
|---|---|---|
| `Member.id` (string nanoid) | `Member.externalId` | microservice stores BIGINT `memberKey` internally |
| `Member.treeId` | `Member.treeKey` | tree scope from path variable |
| `Member.isAlive` | `Member.alive` | name only |
| `Member.avatarUrl` | `MediaObject.legacyAvatarUrl` | read-only fallback |
| `Relationship.sourceMemberId` | `Relationship.fromMemberId` | microservice uses `from`/`to` |
| `Relationship.targetMemberId` | `Relationship.toMemberId` | same |
| `Event.eventDate` | `Event.startDate` | monolith used `date` |
| `MediaMetadata.blobUrl` | `MediaObject.url` | signed URL |
| `MediaMetadata.fileSize` | `MediaObject.size` | |

The mapper (`src/lib/api/dto-mapper.ts`) carries the canonical
`fromSpring*` and `toSpring*` functions plus the `Spring*Dto`
interfaces used by the services.

## What stays in the BFF

- Auth flow (`/api/auth/*`): NextAuth session validation, bridge JWT
  issuance, login/registration/verification redirects.
- RBAC checks (`requireTreePermission`): tree scope + role lookup via
  the gateway.
- Pure algorithms: generation calculation, cycle detection,
  ancestry path — kept in `src/lib/algorithms/` (BFF-local TS).
- `ChangelogService`: forwards audit rows to the gateway's
  `/api/audit/change-logs` endpoint.
- Snapshot/restore/export polling: the BFF polls the gateway for job
  status (`/api/transfer/jobs/{jobId}`).

## What moved out

- `getMembers`, `getRelationships`, etc. — those were Blob readers;
  the BFF now calls `/api/trees/{treeId}/members` through the
  gateway.
- `putMembers`, `putRelationships`, etc. — analogous.
- `treeService.createTree` previously constructed a `FamilyTree` from
  `nanoid()` and wrote it to Blob. It now POSTs to
  `/api/trees` and lets identity-service allocate the external id.

## NextAuth integration

NextAuth itself still uses Vercel Blob for user records (the legacy
`src/lib/auth/user-store.ts`). Identity writes that come from
`/api/auth/register` go through the gateway to identity-service; the
Blob-backed user-store is only the legacy NextAuth adapter that the
BFF reads from to populate the NextAuth session.

This dual-write approach keeps NextAuth sessions working locally
without standing up identity-service. To remove the Blob dependency
altogether, identity-service would need to expose a
`GET /api/internal/auth/users/{id}` endpoint that NextAuth can call
on every request — that's a follow-up, not in scope for this lane.

## Roll-out

- Phase 1: write the gateway-backed services, routes, and DTO mapper
  (done).
- Phase 2: cutover flag in `next.config.mjs` and `src/lib/api/cutover.ts`
  (already in place; the BFF always sends to the gateway).
- Phase 3: kill the legacy Blob-backed paths once every consumer has
  been migrated to the new client services (in flight; depends on
  rollout of identity-service user lookup, see above).
