# NFRs, Data Classification and Threat Model (Task 4)

Status: **Proposed for approval** by security/product/operations. All limits below are
enforced in code/configuration by later tasks; approval of this document is the gate for
Tasks 16, 28 and 33.

## 1. Workload profile (Task 4.1)

Measured/estimated from the legacy deployment (single-family product, Vietnamese locale):

| Dimension | Baseline | Design ceiling |
|---|---|---|
| Concurrent authenticated users | ≤ 25 | 200 |
| Largest tree | ~1,500 members / ~4,000 relationships | 20,000 members / 60,000 relationships |
| Read RPS (steady) | < 5 | 100 |
| Write RPS (steady) | < 1 | 20 |
| Media object size | ≤ 10 MiB (hard limit, frozen) | unchanged |
| Import document | ≤ 25 MiB (frozen) | unchanged |
| Export (PDF/PNG of largest tree) | ≤ 60 s async job | 5 min budget with progress |
| Media concurrency | ≤ 5 parallel uploads/user | 20 |

## 2. SLOs, RPO/RTO, retention (Task 4.4)

| Objective | Target |
|---|---|
| Availability (API, monthly) | 99.5 % |
| Latency p95: reads | ≤ 300 ms (excl. media bytes) |
| Latency p95: writes | ≤ 600 ms |
| Latency p95: media content proxy TTFB | ≤ 800 ms |
| RPO (MySQL, binlog + PITR) | ≤ 5 min |
| RTO (regional restore) | ≤ 4 h |
| User snapshot retention | 30 days (frozen legacy contract) |
| Business audit retention | 400 days |
| Security audit retention | 400 days, WORM storage |
| Idempotency record retention | 7 days |
| Quarantined upload retention | 14 days then delete |
| Residency | Data at a single managed-MySQL region chosen by ops; Vercel Blob remains the binary store (ADR-005) |

Rate thresholds (HTTP 429 with `Retry-After`):
login 10/min/IP + lockout 5/15 min/account (frozen); register 5/h/IP; media upload
30/h/user; import 6/h/tree; export/report jobs 12/h/tree; share-token reads 120/min/token;
global authenticated default 600/min/user.

## 3. Data classification (Task 4.2)

| Class | Data | Handling |
|---|---|---|
| C3 – Secret | password hashes, verification/reset tokens (SHA-256 at rest), internal JWT signing keys, gateway service keys, `BLOB_READ_WRITE_TOKEN` | Secret store only; never logged; never in audit; rotation without rebuild (Task 7) |
| C2 – Sensitive PII | member contact data (phone, email, address), biography/notes, media originals, identity emails, session identifiers | Encrypted at rest + TLS; audit stores field *names* only (allowlist); excluded from public share DTO (ADR-014) |
| C1 – Family-internal | names, dates, relationships, events, albums, statistics, changelogs, backups | Tree-scoped authorization on every access (Req. 16) |
| C0 – Public-by-token | redacted share projection (name, dates, gender, generation, avatar) | Anonymous with unguessable token; `no-store` + `X-Robots-Tag` frozen headers |

Backups inherit the highest class of their content (C2). Security audit is C2.

## 4. Threat model (Task 4.3)

Trust boundaries: (B1) browser ↔ Next.js frontend; (B2) frontend/PWA ↔ Spring API;
(B3) Spring ↔ MySQL (private network); (B4) Spring ↔ Blob control gateway (service auth);
(B5) browser ↔ Blob data plane (signed URL); (B6) NextAuth bridge ↔ Spring (internal JWT);
(B7) cron/workers ↔ Spring (service credential).

| # | Threat | Vector | Controls (owning task) |
|---|---|---|---|
| T1 | IDOR across trees | resource IDs guessable, optional `?treeId=` discovery (DEF-19) | Central tree-scope authorization on the resolved resource, 404-on-denied identical to legacy; same-tree composite FKs (T9, T21); cross-tree tests |
| T2 | CSRF on state changes | cookie-based session on compatibility routes | SameSite=Lax cookies + origin/CSRF token check on non-GET (T16); V2 uses token auth |
| T3 | SQL injection | search/filter inputs, Vietnamese normalization | Parameterized `JdbcClient` only; ArchUnit ban on string-concatenated SQL (T6, T10) |
| T4 | SSRF | import URLs, blob URL fields in import documents | No server-side fetch of user URLs; imports validate but never dereference `blobUrl` (T29); gateway only talks to `blob.vercel-storage.com` |
| T5 | Malicious file upload | MIME spoofing, polyglots, decompression bombs | Frozen magic-byte checks + 10 MiB cap + quarantine until callback verification (`BLOB_WEBHOOK_PUBLIC_KEY`), thumbnail via sandboxed decoder limits (T13–T15) |
| T6 | Share-token leakage | referrer, logs, search indexing | Unguessable ≥128-bit token, `no-store`, `X-Robots-Tag`, revocation, redacted DTO (ADR-014), token never logged (T33) |
| T7 | Account takeover | credential stuffing, timing oracles, token theft | bcrypt 12, dummy-hash timing defense, lockout 5/15 min, rate limits, SHA-256-at-rest one-time tokens, session idle 30 min (T17–T19) |
| T8 | Import bombs | zip-of-death JSON/GEDCOM, quadratic parsers | 25 MiB pre-parse cap, streaming parser limits, record-count ceilings per design ceiling, job time budget + cancellation (T29) |
| T9 | Backup exfiltration | snapshot endpoints, blob paths | ADMIN-only tree role required, signed exact-path URLs with narrow expiry, security-audit every restore/download (T34) |
| T10 | Signed-URL abuse | replay, path substitution | Exact-path + method + content-type binding, ≤ 5 min expiry, no-overwrite, capability cannot authorize another path/operation (T13) |
| T11 | Injection into audit / log forging | user text with control chars | Field allowlists, JSON-encoded structured logging, forbidden-value tests (T12) |
| T12 | Privilege escalation in tree | role tampering, owner demotion | Server-side role checks; `OWNER_ROLE_IMMUTABLE` invariant (frozen 409); memberships mutable by ADMIN only (T21–T22) |
| T13 | Secret leakage | logs, Actuator, error bodies | Redacted config output, sanitized error envelope (internal exceptions never leak), Actuator behind auth (T6, T7, T16) |
| T14 | Identity split-brain during migration | dual writers for users/sessions | Single-writer cutover per ADR-009; migration ledger records the writer for every state (T17–T20) |

## 5. Approval record (Task 4.4)

| Role | Decision | Notes |
|---|---|---|
| Security | pending sign-off | includes DEF-01/DEF-18 corrections from the defect register |
| Product | pending sign-off | SLOs and frozen limits above |
| Operations | pending sign-off | RPO/RTO, retention, residency, rate thresholds |

Changes to any frozen limit (10 MiB media, 25 MiB import, 30-day snapshots, lockout
policy) require a new revision of this document plus an OpenAPI-baseline diff review.
