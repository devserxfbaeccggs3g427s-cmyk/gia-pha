# Cutover / Rollback Rehearsal (Twice)

Spec: spring-boot-backend-migration — Task 49 (Req 20.6).

## Goal

Two consecutive production-like rehearsals that meet the approved RPO/RTO
objectives and exercise every operator-facing action end-to-end. Each
rehearsal produces evidence for engineering, DBA, security, and operations
sign-off.

## Rehearsal 1

1. Restore the most recent production-like source snapshot into staging.
2. **Cutover** — extract → freeze → final-delta → reconcile → switch.
3. Operate for 4 hours; observe SLOs and parity dashboards.
4. **Rollback** — atomic flip + reverse projection.
5. Operate for 2 more hours; verify legacy writer works again.
6. **Second cutover** — repeat with the second cohort (larger tree).
7. **Failure injection**: kill a MySQL primary, throttle the Blob gateway,
   rotate the bridge key, push a corrupt source.

## Rehearsal 2

Repeat the full cycle with different failure injections:

- Bridge issuer misconfiguration.
- Outbox worker backlog.
- Reconciliation drift introduced by hand.
- Scanner outage during binary replication.

## Measurement

| Metric | Target | Recorded |
| --- | --- | --- |
| Extract → reconcile time | < 30 minutes | … |
| Reconcile blocking discrepancies | 0 | … |
| Switch rollback time (writer flip) | < 60 seconds | … |
| Reverse projection lag | < 5 minutes | … |
| API SLO during transition | within budget | … |
| Binary replication RPO | ≤ 24 hours | … |
| Restore RTO | ≤ 4 hours | … |

## Sign-off

- Engineering: cutover command + reconciliation report.
- DBA: MySQL PITR rehearsal evidence.
- Security: ASVS gates and bridge token rotation evidence.
- Operations: dashboard screenshots + alert test log.
