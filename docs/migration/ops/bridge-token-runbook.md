# Bridge Token Operator Runbook

Spec: spring-boot-backend-migration — Task 18 (Req 2.5–2.7, ADR-009).

## Overview

The NextAuth bridge exchanges the encrypted NextAuth session for a short-lived
ES256 JWT consumed by Spring during the strangler migration. The bridge is the
sole identity authority before the global identity cutover (Task 20/51).

```
browser ─NextAuth session─▶ Next.js (/api/auth/bridge) ─ES256 JWT─▶ Spring
                                  │                                   │
                                  │ validates NextAuth session       │ verifies signature,
                                  │ server-side                       │ expiry, replay
```

## Token shape

* Algorithm: `ES256` (P-256 + SHA-256 ECDSA), JOSE compact serialization
* Lifetime: ≤ **5 minutes** (Req 2.5 — frozen in `BridgeTokenService.MAX_LIFETIME`)
* Pinned claims: `iss`, `aud`, `sub`, `iat`, `nbf`, `exp`, `jti`, `str`
* `kid` header selects the verification key; multiple keys valid at once
* `jti` is single-use — `BridgeReplayStore` rejects replays for the lifetime
* `str` carries `SESSION` / `REAUTHENTICATED` / `HARDWARE` step-up strength

## Configuration

| Env var | Purpose |
| --- | --- |
| `GIAPHA_AUTH_BRIDGE_ISSUER` | Issuer expected on incoming JWTs |
| `GIAPHA_AUTH_BRIDGE_AUDIENCE` | Audience expected on incoming JWTs |
| `GIAPHA_AUTH_BRIDGE_PUBLIC_KEYS_<KID>` | `{"x":"...","y":"..."}` JWK coordinates |
| `GIAPHA_AUTH_BRIDGE_PRIVATE_KEYS_<KID>` | `{"d":"...","x":"...","y":"..."}` for the active signer |
| `GIAPHA_AUTH_BRIDGE_KILL_SWITCH` | `true` to refuse bridge traffic at startup |

## Cookie contract

| Cookie | Value | Attributes |
| --- | --- | --- |
| `gp-bridge` | Compact JWS | `HttpOnly; SameSite=Lax; Path=/api; Max-Age=300; Secure` (production) |

The cookie is `HttpOnly`, `SameSite=Lax`, scoped to `/api` and expires with the
token. It is never written to `localStorage` (Req 2.6). The bridge clearing
endpoint `POST /api/auth/bridge/clear` removes the cookie on logout/session loss.

## Key rotation (no deployment required)

1. **Generate** the new key pair (`openssl ecparam -name prime256v1 -genkey`).
2. **Add** the new public key in the secret store (e.g.
   `GIAPHA_AUTH_BRIDGE_PUBLIC_KEYS_2026-Q4-<id>`).
3. **Move** the new private key to index 0 of `GIAPHA_AUTH_BRIDGE_PRIVATE_KEYS_*`.
4. **Roll** pods (configtree re-read). Both `kid`s verify; the new one signs.
5. Wait ≥ max-lifetime (5 minutes) for outstanding bridge tokens to expire.
6. **Remove** the old public/private key entries and roll again.

`BridgeKeyRegistry.rotateActiveKey()` is also exposed for in-process rotation
during drills. The active signing key never appears in logs or the Actuator
surface.

## Kill switch

`POST /api/internal/bridge/kill-switch?enabled=true` (operator authority)
disables issuance and verification with no rebuild:

```
status=$(curl -s -X POST \
  -H "Authorization: Bearer $OPS_TOKEN" \
  "https://spring.example/api/internal/bridge/kill-switch?enabled=true")
```

Inspect current state with `GET /api/internal/bridge`. Recovery is the same
endpoint with `enabled=false`. The kill switch is in-memory only; a pod
restart naturally re-enables the bridge unless `GIAPHA_AUTH_BRIDGE_KILL_SWITCH`
is set.

## Failure modes

| Symptom | Likely cause | Action |
| --- | --- | --- |
| 401 `INVALID_TOKEN` after deployment | New signing key not yet in verification set | Add public key, redeploy |
| 503 `BRIDGE_DISABLED` | Kill switch is active | `POST /api/internal/bridge/kill-switch?enabled=false` |
| Replay rejection storms | Suspected token capture | Engage kill switch, rotate key, audit `security_audit_logs` |
| `kid` header missing | Old bridge client | Upgrade the Next.js signer to include `kid` |

## DoD evidence (Task 18)

* `BridgeTokenService` pins issuer, audience, algorithm, signature key,
  expiry and replay behavior.
* `BridgeKeyRegistry` accepts multiple verification keys concurrently so
  rotation never blocks signing.
* `BridgeAuthenticationFilter` reads the `Authorization: Bearer` header or
  the `gp-bridge` cookie and emits a uniform 401 envelope on rejection.
* The Next.js exchange endpoint `/api/auth/bridge` only returns the token
  inside an `HttpOnly; SameSite=Lax; Secure` cookie scoped to `/api`; the
  clear endpoint `/api/auth/bridge/clear` removes it on logout.
* `BridgeTokenService` rejects tokens outside the configured max-lifetime
  window with a 5-second clock-skew tolerance, even when the signature is
  valid.
* The kill switch (`POST /api/internal/bridge/kill-switch`) disables
  issuance and verification without a deployment.
