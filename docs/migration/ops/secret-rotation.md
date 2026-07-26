# Secret and Key Rotation Runbook

Spec: spring-boot-backend-migration — Task 7 (Req 15.3, 15.9, 15.13).

## Principles

- Secrets live only in the platform secret store, mounted as a configtree at `/run/secrets/`
  (or injected as `GIAPHA_*` environment variables). Images never contain secrets; rotation
  never requires a rebuild (Task 7 DoD).
- Every verification-side secret is a **list**: index 0 is the active value used for
  signing/sending; *all* entries are accepted for verification. This enables overlap windows
  during rotation with zero downtime.
- Actuator redacts all values (`show-values: never`); logs must never print configuration
  (enforced by review + the log-scrubbing check in Task 16).

## Rotatable material

| Property | Used for | Rotation style |
| --- | --- | --- |
| `giapha.auth.bridge-public-keys` | Verifying NextAuth bridge JWTs (ES256, ADR-009) | Add new public key → deploy → switch Next.js signer key → remove old key |
| `giapha.auth.session-secrets` | Spring-issued session token HMAC | Prepend new secret → deploy → wait ≥ `session-ttl` → remove old |
| `giapha.blob-gateway.secrets` | HMAC signing of gateway requests (ADR-006) | Prepend new secret on both gateway and Spring → deploy both → remove old |
| `giapha.email.resend-api-key` | Transactional email | Replace value → deploy (single-valued; provider tolerates brief overlap) |
| `spring.datasource.password` / `spring.flyway.password` | MySQL identities (Task 8.3) | MySQL dual-password (`ALTER USER ... RETAIN CURRENT PASSWORD`) → update store → roll pods → `DISCARD OLD PASSWORD` |
| OAuth client secrets | Google/Facebook | Create second secret at provider → update store → roll → delete old at provider |

## Rolling rotation procedure (generic)

1. Generate the new value (`openssl rand -base64 32` for HMAC secrets; provider console for
   OAuth; `ALTER USER` for MySQL).
2. **Add** the new value at list index 0 in the secret store, keeping the old value in the list.
3. Roll the deployment (pods restart, configtree re-read). Both values now verify; the new one
   signs.
4. After the overlap window (max outstanding token/session lifetime), **remove** the old value
   and roll again.
5. Record the rotation (date, operator, property) in the ops log; confirm
   `security_audit_logs` shows no verification failures during the window.

## Incident: committed secret in `.env.example`

The repository previously committed a literal `NEXTAUTH_SECRET` value in `.env.example`
(removed in Task 7.4). Treat that value as compromised:

- [ ] Rotate `NEXTAUTH_SECRET` in every Vercel environment that ever used the committed value
      (Vercel dashboard → Settings → Environment Variables), using the procedure above.
- [ ] Invalidate active NextAuth sessions after rotation (users re-login).
- [ ] Verify no other literal matches the old value anywhere in configuration stores.

Status: pending operator execution — code-side remediation (empty placeholder + this runbook)
is complete.
