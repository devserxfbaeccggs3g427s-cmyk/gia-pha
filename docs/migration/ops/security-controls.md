# Platform Security Controls — ASVS 5.0 L2 Evidence Map

Task 16 (Requirement 15). This document is the control-evidence record required by
the Task 16 Definition of Done: for each applied control it names the enforcing
artifact so reviewers and the pre-cutover pentest (Req 15.12) can verify
implementation rather than intent. Legacy-frozen values (bcrypt 12, lockout
5/15 min, 10 MiB media, 25 MiB import) come from `docs/migration/nfr-threat-model.md`.

Module paths below are relative to `backend/` unless stated otherwise.

## 1. Authentication & session boundary (ASVS V2/V3, OWASP API2)

| Control | Evidence |
| --- | --- |
| Deny-by-default authorization: every route requires authentication except health, the HMAC-verified upload-completion callback, and OPS-gated actuator | `app-bootstrap/.../config/SecurityConfig.java` (`authorizeHttpRequests`, `anyRequest().authenticated()`) |
| Stateless sessions; no default HTTP Basic / auto-generated users usable over HTTP | `SecurityConfig.java` (`SessionCreationPolicy.STATELESS`, `httpBasic` removed) |
| 401/403 responses use the frozen legacy envelope, no stack traces or internals | `SecurityConfig.java` entry point / access-denied handler via `app-bootstrap/.../web/EnvelopeResponseWriter.java` |
| Login lockout 5 failures / 15 min per account (frozen legacy) | Implemented with identity persistence (Task 17); threshold frozen in `nfr-threat-model.md` §2 |
| Internal service-to-service calls authenticated by HMAC with ±5 min skew + nonce replay protection, not user credentials | `binary-storage/.../adapter/in/web/UploadCompletionController.java`; `gateway/blob-gateway/` HMAC scheme |

## 2. CSRF & CORS (ASVS V4, OWASP API8)

| Control | Evidence |
| --- | --- |
| CSRF protection for cookie-authenticated unsafe methods: SPA double-submit cookie (`XSRF-TOKEN` / `X-XSRF-TOKEN`), `SameSite=Lax` | `SecurityConfig.java` (`CookieCsrfTokenRepository`, `SpaCsrfTokenRequestHandler`) |
| Bearer-authenticated requests exempt (structurally CSRF-immune); `/api/internal/**` exempt (HMAC, no cookies) | `SecurityConfig.requiresCsrf` |
| CORS deny-by-default: zero `Access-Control-Allow-*` headers unless an exact-origin allowlist is configured; no wildcards; credentials only with explicit origins | `SecurityConfig.corsConfigurationSource`, `giapha.security.allowed-origins` in `application.yml` |

## 3. Security headers & transport (ASVS V14, Req 15.2/15.5)

| Control | Evidence |
| --- | --- |
| `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'` (pure JSON API) | `SecurityConfig.java` headers section |
| `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, restrictive `Permissions-Policy` | `SecurityConfig.java` headers section |
| HSTS `max-age=31536000; includeSubDomains` (effective once TLS terminates at the edge) | `SecurityConfig.java`; `server.forward-headers-strategy` in `application.yml` |
| TLS everywhere / private MySQL networking | Deployment control: edge TLS termination + `docker-compose` private network locally; production MySQL reachable only from the app network (see `docs/migration/ops/mysql-environments.md`) |
| API responses marked `Cache-Control: private, no-store` and correlation ID on every response | `app-bootstrap/.../web/ApiResponseHeadersFilter.java`, `CorrelationIdFilter.java` |

## 4. Rate limiting & abuse protection (ASVS V11, OWASP API4, Req 15.7)

Thresholds are frozen in `nfr-threat-model.md` §2; all denials return the legacy
envelope with code `RATE_LIMIT`, HTTP 429, and a `Retry-After` header (the legacy
frontend already treats 429 as retryable).

| Route class | Limit / window / key | Evidence |
| --- | --- | --- |
| Login & auth mutations | 10 / min / IP | `RateLimitFilter.java` + `GiaPhaProperties.Limits.authRateLimit` |
| Registration | 5 / hour / IP | `registerRateLimit` |
| Media uploads | 30 / hour / user | `uploadRateLimit` |
| Import execute | 6 / hour / user (documented deviation: tree ID lives in the body; per-user is at least as strict) | `importRateLimit` |
| Export / report / backup jobs | 12 / hour / tree | `exportRateLimit` |
| Share-token reads | 120 / min / token (keyed by SHA-256 prefix — raw tokens never enter limiter state) | `shareRateLimit` |
| Global authenticated backstop | 600 / min / user | `globalRateLimit` |

Algorithm: sliding-window counter, race-free under concurrency, amortized pruning —
`platform-kernel/.../ratelimit/SlidingWindowRateLimiter.java` (JDK-only, unit-testable
without framework). The filter runs after the security chain so authenticated traffic
keys by user ID, and exempts `/api/internal/**` so Blob event redelivery is never starved.

## 5. Input bounds & injection defense (ASVS V5, OWASP API3/API6, Req 15.8)

| Control | Evidence |
| --- | --- |
| All SQL is parameterized (`JdbcClient` / named parameters); no string-concatenated SQL | Repository convention, e.g. `audit-operations/.../adapter/out/mysql/MySqlOutboxRepository.java`; enforced by review + CodeQL SAST |
| Request-body size caps: 25 MiB import, 10 MiB media/internal-blob, 1 MiB default — enforced on declared `Content-Length` *and* on actual streamed bytes (chunked bodies) | `app-bootstrap/.../web/RequestBodySizeFilter.java`; caps in `GiaPhaProperties.Limits` |
| Oversize bodies → HTTP 413, envelope code `FILE_TOO_LARGE` | `GlobalExceptionHandler.java` (`bodyTooLarge`, `MaxUploadSizeExceededException`) |
| JSON parser bounds: nesting ≤ 64, token count, string/number/name lengths, document length | `spring.jackson.factory.constraints.*` in `application.yml` |
| Header size 16 KiB, connection timeout 10 s, form-post 64 KiB, swallow cap, multipart caps | `server.*` / `spring.servlet.multipart.*` in `application.yml` |
| Enum/format/range validation with legacy-compatible `VALIDATION` envelopes | `GlobalExceptionHandler.java` + Bean Validation on request records |
| Content-type allowlist for media (JPEG/PNG/WebP/PDF) + quarantine-scan-promote before any URL is served | Task 15: `binary-storage` upload-intent pipeline, `MalwareScanner` port |

## 6. Secrets & configuration (ASVS V6/V14, Req 15.3/15.13)

| Control | Evidence |
| --- | --- |
| Secrets from environment / mounted secret files only; app fails fast when absent; no permissive defaults | `application.yml` (`spring.config.import: optional:configtree:/run/secrets/`, `${GIAPHA_DB_*}` without defaults) |
| Rotation-friendly key lists (HMAC secrets, session secrets, bridge keys are ordered lists; index 0 signs, all verify) | `GiaPhaProperties.java`; `docs/migration/ops/secret-rotation.md` |
| `NEXTAUTH_SECRET` scrubbed from the committed env template | root `.env.example` (empty placeholder) |
| Actuator values masked even if endpoints exposed; error responses never include messages/stacktraces | `application.yml` (`show-values: never`, `server.error.include-*: never`) |
| Blob read-write token held only by the gateway function, never by Spring | ADR-006; `gateway/blob-gateway/` |

## 7. Logging & data protection (ASVS V7/V9, Req 15.9)

| Control | Evidence |
| --- | --- |
| Central redaction of bearer tokens, secret-named pairs, signed-URL query strings, and emails in every log message and stack trace | `platform-kernel/.../logging/LogSanitizer.java`; `app-bootstrap/.../logging/Redacting*Converter.java`; `logback-spring.xml` |
| Blob pathnames never logged raw — 16-char SHA-256 `pathHash` convention | binary-storage services/workers (Tasks 14–15) |
| Audit records use field allowlists per entity, never full payloads | `audit-operations` audit writer (Task 12) |
| Data classification C0–C3 with handling rules | `nfr-threat-model.md` §3 |
| Retention & erasure workflows (idempotent workers) | Scheduled with Task 28 (change logs) / Task 15 cleanup workers; schedule frozen in `nfr-threat-model.md` |

## 8. Supply chain & CI gates (Req 15.10/15.11/15.16)

| Control | Evidence |
| --- | --- |
| SAST (CodeQL, security-extended, Java + JS/TS), secret scanning (Gitleaks, full history), SCA/IaC/misconfig (Trivy fs) | `.github/workflows/security-scans.yml` |
| Merge blocked on unresolved CRITICAL/HIGH findings | Trivy `exit-code: 1`; CodeQL PR alerts |
| SBOM (CycloneDX 1.6 JSON, aggregate) generated every build and retained 90 days for license review | `backend/pom.xml` cyclonedx-maven-plugin; `sbom` job in the workflow |
| Reproducible builds (fixed `project.build.outputTimestamp`), enforcer-pinned Java 25 / Maven 3.9+, upper-bound dependency convergence | `backend/pom.xml` |
| Waivers require recorded rationale | Workflow header comment; PR review policy |

## 9. Management & operational surface (Req 15.2)

| Control | Evidence |
| --- | --- |
| Actuator on a separate non-public port (8081), only `health,info,prometheus` exposed, probes enabled, details hidden | `application.yml` `management.*` |
| Actuator over the main chain requires the `OPS` authority | `SecurityConfig.java` (`EndpointRequest.toAnyEndpoint()`) |
| Graceful shutdown; virtual threads (no thread-pool exhaustion amplification) | `application.yml` (`server.shutdown`, `spring.threads.virtual.enabled`) |

## 10. Open items (tracked, not yet in force)

- Login lockout + credential storage (bcrypt 12) land with identity persistence (Tasks 17–20).
- Frontend must echo `X-XSRF-TOKEN` once cookie sessions arrive (Task 36).
- Pre-cutover penetration test and finding remediation (Req 15.12) is a cutover
  gate tracked in Task 48; this document is its input inventory.
- Erasure/anonymization workflow (Req 15.15) ships with change-log migration (Task 28).
