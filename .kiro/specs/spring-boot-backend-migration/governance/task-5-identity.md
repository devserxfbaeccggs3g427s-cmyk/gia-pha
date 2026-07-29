# Task 5 — Identity Service and Transition Bridge

The Identity service is the **reference implementation** of the
service template. It is the first service cut over to the new
platform; all other services replicate its structure (Task 4 +
`tooling/service-bootstrap.sh`).

## 5.1 Domain

`services/identity-service/src/main/java/com/familya/identity/domain/`

- `model/User` — aggregate root: id, normalized email, BCrypt hash,
  verification state, lockout state, failed attempts, version.
- `model/Session` — opaque session with absolute and idle expiry.
- `model/OAuthLink` — provider linkage (no raw tokens persisted).
- `event/IdentityUserCreated`, `IdentityUserVerified`,
  `IdentityUserLocked`, `IdentityCredentialsRehashed`.
- `exception/EmailAlreadyExistsException`,
  `AccountLockedException`, `InvalidCredentialsException`,
  `RateLimitExceededException`.

## 5.2 Use cases

- `RegisterUserUseCase` — rate-limit, uniqueness, hash, outbox.
  Accepts the same `Idempotency-Key` recorded by the Gateway and
  raises a `409` on conflicting payload hash.
- `AuthenticateUseCase` — verifies BCrypt, applies lockout after
  `familya.identity.failed-attempts-limit` (default 5) failures for
  `familya.identity.lockout-window-minutes` (default 15), rehashes
  on every successful login, issues a `Session` whose cookie is
  `HttpOnly; Secure; SameSite=Lax`.
- `VerifyEmailUseCase` — flips verification state and publishes
  `IdentityUserVerified`.
- `RevokeSessionUseCase` — revokes a session after checking that
  the acting user owns it.

## 5.3 NextAuth bridge

`adapter/in/security/IdentitySecurityConfig.java` validates the
short-lived asymmetric internal token (`X-NextAuth-Bridge-Token`).
Tokens are RS256, audience-bound, lifetime ≤ 5 minutes. The public
key is published to the platform JWKS. The token is never persisted
in browser storage and never logged.

A kill switch is exposed via the `familya.identity.bridge.required`
flag (default `true`). Setting it to `false` rejects all bridge
requests with 401 — used during the cutover rollback drill.

## 5.4 Identity events

`adapter/out/events/OutboxIdentityEventPublisher.java` stages every
event into the local outbox in the same transaction as the aggregate
mutation. The platform outbox relay publishes to
`identity.events.v1` with partition key `userId` and the required
metadata headers.

## 5.5 Identity cutover

`infra/runbooks/identity-cutover.md` records the rehearsal. The
cutover sequence is:

1. Freeze legacy writers.
2. Apply the final delta.
3. Reconcile `users` and `oauth_link` against the legacy source.
4. Compare-and-set the route authority at the Gateway.
5. Unfreeze.

Exactly one writer is allowed for `users` and `oauth_link`. A
dual-writer alarm fires when the legacy service writes to
`users` after the cutover window opens.

## Acceptance

- `mvn -B -ntp -f services/identity-service/pom.xml -DskipTests package` succeeds.
- The ArchUnit test passes.
- `mvn -B -ntp -f services/identity-service/pom.xml flyway:migrate` against
  MySQL 8.4 Testcontainers succeeds.
- `mvn -B -ntp -f services/identity-service/pom.xml jooq-codegen:generate` succeeds.
- Golden identity contract (BCrypt, lockout, rehash) passes the
  property tests under `contracts/fixtures/identity/`.
- Cutover rehearsal twice meets the rollback criteria.
