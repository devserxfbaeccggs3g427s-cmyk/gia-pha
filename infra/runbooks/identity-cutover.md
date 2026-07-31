# Identity Cutover Runbook

This runbook records the production cutover of the Identity service.
Two rehearsals are required before production writes; the rehearsal
must succeed twice in a row to satisfy the Go/No-Go gate (Task 18).

## Pre-flight checklist

- [ ] Environment ADR accepted (`environments/example/ADR.md`).
- [ ] Baseline spike verdict PASS.
- [ ] Identity service deployed and READY in staging.
- [ ] Dual-writer alarm registered at the platform observability stack.
- [ ] Legacy writer read-only; `users` writes are stopped.
- [ ] Reconciliation report shows
      `source = accepted + quarantined + approved duplicate` (Task 15).
- [ ] Rollback plan approved by SRE Lead.

## Cutover steps

1. **Freeze.** Set `familya.identity.legacy-writers-enabled=false`
   at the Gateway. The Gateway returns `503` to any legacy writer.
2. **Final delta.** Run the migration loader one last time to pull
   any rows that landed after the last delta sync.
3. **Reconcile.** Execute the identity cutover reconciliation
   endpoint and assert `users` and `oauth_link` row counts match
   the legacy source within 0 rows.
4. **Compare-and-set.** Set `familya.identity.route-authority=new`
   at the Gateway. The Gateway now routes `/api/v2/identity/**`
   to the new service exclusively.
5. **Unfreeze.** Resume normal operation.

## Validation

- Issue 100 synthetic logins; verify exactly 100 `IdentityUserLocked`
  or `IdentityUserCreated` events on the new topic, none on the
  legacy database.
- Verify that the dual-writer alarm is silent.
- Verify that the projection consumers in Tree Access, Member, and
  Audit Ops have caught up (consumer lag < 1s for 5 minutes).

## Rollback

If any validation step fails:

1. Set `familya.identity.route-authority=legacy` at the Gateway.
2. Re-enable `familya.identity.legacy-writers-enabled=true`.
3. The legacy writers replay the latest `outbox_record` snapshot
   for the identity topic and resume authoritative writes.
4. Investigate; do not re-attempt cutover within 4 hours.
