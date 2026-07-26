# Per-Tree Single-Writer Canary

Spec: spring-boot-backend-migration — Task 48 (Req 20.3, 20.5).

## Procedure

1. **Select a canary tree** that is not the operator's own. Internal/test
   trees are favored; never start with the largest tree.
2. **Freeze**: `POST /api/ops/identity-authority/freeze?reason=canary-{treeId}`
   sets the writer to TRANSITION. The legacy writer is now read-only.
3. **Final-delta**: `GIAPHA_IDENTITY_MODE=FINAL_DELTA GIAPHA_IDENTITY_USERS_JSON=…
   java -jar giapha-backend.jar` re-runs the importer in delta mode so any
   drift since the last sync is reconciled.
4. **Reconcile**: `POST /api/ops/identity-authority/reconcile` returns the
   per-tree reconciliation report. Zero blocking discrepancies required.
5. **Switch ownership**: `POST /api/ops/identity-authority/switch-to-spring
   ?reason=canary-{treeId}` flips the writer atomically.
6. **Reverse projection** is enabled for the rollback window so the legacy
   store remains a valid fallback.
7. **Observe SLO / parity / security / projection lag** for at least the
   configured observation window (default: 24 hours).

## Stop conditions

Any of the following auto-rolls back the canary:

- Two writers attempt a row update simultaneously.
- A blocking reconciliation discrepancy opens.
- An SLO breach (auth latency, API error rate, reconciliation drift)
  persists for > 30 minutes.
- A security event of severity BLOCKED.
- Reverse projection lag exceeds the rollback RPO.

## Rollback

`POST /api/ops/identity-authority/rollback-to-legacy?reason=canary-rollback
-{treeId}` atomically flips the writer back. The reverse projection
already maintains the legacy store, so the rollback is immediate.
