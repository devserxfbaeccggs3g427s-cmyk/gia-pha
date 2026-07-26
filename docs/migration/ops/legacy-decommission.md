# Legacy Decommission

Spec: spring-boot-backend-migration — Task 53 (Req 20.7-20.11).

## Preconditions

- Rollback window has elapsed.
- Zero-traffic evidence: 30 consecutive days of `users.json` writes = 0
  (verified via `security_audit_logs`).
- All production trees migrated to Spring single-writer.
- ASVS Level 2 evidence complete.
- Operator + business sign-off.

## Steps

1. **Remove Next.js business handlers and proxies** that are now
   exclusively Spring. Public pages (`/`, `/share/[id]`, `/login`,
   `/register`, `/verify-email`) may remain Next.js; only the JSON API
   handlers and their middleware can be retired.
2. **Remove Blob JSON readers/writers** — the
   `giapha.blob.paths.usersJson` and `giapha.blob.paths.treesJson`
   references must be empty. Verify with `grep` across the repository.
3. **Remove legacy cron** jobs that refreshed the Blob JSON snapshots.
4. **Remove the reverse projector** (`IdentityRollbackProjector` and any
   per-tree reverse projector) once the rollback window has closed.
5. **Revoke credentials**:
   - `NEXTAUTH_SECRET`
   - Bridge signing keys
   - Blob gateway service-auth keys
   - Legacy cron service-account credentials
6. **Archive or delete source data** under the approved retention
   policy. Document owner + deletion date in
   `docs/migration/ops/legacy-decommission-evidence.md`.

## Verification

- Static analysis: `dependency-check` reports zero references to
  `giapha.blob.paths.usersJson`.
- Runtime analysis: trace sampling over 30 days shows no
  Blob JSON write/read.
- Capability review: `git grep -l "users.json"` returns zero hits in
  business code.
