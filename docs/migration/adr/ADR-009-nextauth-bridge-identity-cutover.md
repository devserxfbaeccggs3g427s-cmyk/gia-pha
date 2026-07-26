# ADR-009: NextAuth bridge followed by global identity single-writer cutover

Status: Accepted · 2026-07 · Requirements: 2, 15

## Context
The frontend authenticates with NextAuth (JWT strategy, 30-min idle). Spring must
authorize compatibility requests from day one, but identity data (users, credentials,
verification tokens) cannot have two writers.

## Decision
Two phases:
1. **Bridge**: NextAuth remains the authentication authority. A Next.js server-side
   bridge exchanges the NextAuth session for a short-lived **asymmetric internal JWT**
   (ES256; private key only in the bridge, public key in Spring). Spring validates
   signature/expiry/audience and maps claims to its principal. Identity writes (register,
   verify, lockout counters) stay in the legacy path until identity cutover.
2. **Cutover**: a one-shot migration moves users/credentials/tokens to MySQL
   (Tasks 17, 20); from that instant Spring is the only identity writer and NextAuth's
   credentials provider calls Spring's authenticate endpoint; later Spring issues
   sessions directly (Task 19).

## Alternatives
- Dual-write users to Blob JSON and MySQL — rejected: split-brain on lockout counters
  and verification tokens; violates single-writer DoD.
- Big-bang replacement of NextAuth — rejected: couples frontend auth rewrite to backend
  cutover, no rollback.
- Shared symmetric secret — rejected: bridge compromise would let anyone mint tokens
  Spring trusts; asymmetric keeps signing capability out of Spring entirely.

## Diagram
```
Phase 1: browser ─NextAuth cookie─▶ Next.js ─ES256 internal JWT─▶ Spring (verify pub key)
Phase 2: browser ─NextAuth cookie─▶ Next.js ─credentials calls──▶ Spring (identity writer)
```

## Consequences
Internal JWT TTL ≤ 5 min, no refresh (bridge re-issues per request batch); key rotation
via JWKS-style dual-key window; identity cutover is reversible until legacy users.json is
frozen.

## Failure modes
Bridge key leak → rotate key pair; Spring rejects old `kid` after window. Clock skew →
±60 s leeway, NTP required. Cutover discrepancy → reconciliation compares users.json to
MySQL before flipping the writer flag.

## Rollback
Phase-2 rollback: re-point NextAuth credentials provider to legacy auth-service and mark
MySQL identity read-only; ledger records the flip both ways.

## Single writer
The `identity_authority` ledger row states LEGACY or SPRING at every instant; both code
paths check it before any identity write.
