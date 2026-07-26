# Dark Deployment & Production Cutover

Spec: spring-boot-backend-migration — Task 50-51 (Req 12, 15-18, 20).

## Dark deployment (Task 50)

1. **Provision**: managed MySQL 8.4 with HA + encrypted backups + binlogs,
   Spring compute, worker capacity, secrets, routing, observability through
   reviewed IaC (`terraform/`, `k8s/`).
2. **Deploy signed image without user traffic**:
   ```bash
   kubectl apply -k k8s/overlays/prod
   kubectl rollout status deploy/giapha-backend -n giapha
   ```
3. **Apply Flyway**: runs automatically at boot; the dark instance does
   not serve user traffic.
4. **Verify connectivity**: synthetic contracts, control-gateway
   handshake, scheduler isolation, resource limits.
5. **Capture restore point + immutable source manifest** before
   exposing any traffic.

## Progressive production cutover (Task 51)

1. **Identity cutover** (single-shot, global):
   ```bash
   # final-delta
   GIAPHA_IDENTITY_MODE=FINAL_DELTA \
     GIAPHA_IDENTITY_USERS_JSON=s3://migration/users.json \
     kubectl exec -ti deploy/giapha-backend -- \
     java -jar giapha-backend.jar --spring.profiles.active=identity-cutover
   ```
2. **Switch authority**:
   ```bash
   curl -X POST "$OPS_BASE/api/ops/identity-authority/freeze?reason=prod-identity"
   curl -X POST "$OPS_BASE/api/ops/identity-authority/switch-to-spring?reason=prod-identity"
   ```
3. **Tree cohorts** (low → higher risk):
   - internal/test trees
   - small (< 100 members) low-traffic trees
   - medium (100-1000 members) trees
   - large (> 1000 members) trees
   ```bash
   for tree in $TREES; do
     curl -X POST "$OPS_BASE/api/ops/trees/$tree/canary/start?reason=cohort-N"
     # observe for 24h
     if ! curl -s "$OPS_BASE/api/ops/trees/$tree/canary/status" | jq -e '.healthy'; then
       curl -X POST "$OPS_BASE/api/ops/trees/$tree/canary/rollback?reason=auto-stop"
       break
     fi
   done
   ```

## Auto-stop conditions

Any of these stops the cohort immediately and rolls back the latest tree:

- Security event of severity BLOCKED.
- Two-writer detection (impossible by design — detected by audit
  comparison).
- Blocking reconciliation discrepancy.
- API 5xx rate > 1% sustained over 5 minutes.
- Auth latency p95 > 500 ms for 3 consecutive polls.
- Reverse-projection lag > 5 minutes.

## Rollback

```bash
curl -X POST "$OPS_BASE/api/ops/identity-authority/rollback-to-legacy?reason=prod-rollback"
```

Each tree's rollback uses the same `canary/rollback` endpoint.
