# ADR-014: Strict allowlisted public share DTO

Status: Accepted · 2026-07 · Requirements: 10, 15 · Supersedes legacy behavior (DEF-01)

## Context
Legacy `GET /api/share/{token}` returns full member records — phone, email, address,
biography — to any anonymous token holder. Tokens travel in URLs (referrers, chat logs).
This is the one place the migration deliberately breaks byte-parity, with security
sign-off (threat T6).

## Decision
The public projection is built from an explicit **allowlist DTO** (fail-closed:
serializer maps named fields only, never the entity):
`fullName, nickname, gender, generation, dateOfBirth*, dateOfDeath*, isAlive, avatar
thumbnail reference` plus relationship/event structure without private notes.
(*dates configurable to year-only by the tree owner in V2.)
Frozen headers stay: `Cache-Control: private, no-store`, `X-Robots-Tag: noindex,
nofollow, noarchive`; revoked/expired tokens stay 404.

## Alternatives
- Preserve full projection for parity — rejected by security; defect register DEF-01
  classifies it `security-correct`.
- Blocklist sensitive fields — rejected: new entity fields would leak by default.
- Authenticated-only sharing — rejected: link sharing is a core product feature.

## Consequences
Shadow-comparison for this endpoint uses a transformed oracle (legacy response projected
through the same allowlist) instead of raw byte-diff; frontend share page consumes only
allowlisted fields already (verified in frontend-call-map).

## Failure modes
Serializer bypass via nested objects → DTO is a flat record composed field-by-field;
contract test asserts the exact JSON key set (any extra key fails).

## Rollback
Flag `compat.share.full-projection` can restore legacy output during shadow comparison
only; the flag is deleted at decommission and cannot be enabled in production profile.

## Single writer
Share links themselves follow tree cutover (ADR-013); token issuance/revocation writes
belong to the owning stack.
