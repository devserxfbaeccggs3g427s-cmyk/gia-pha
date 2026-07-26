# Final Post-Migration Audit

Spec: spring-boot-backend-migration — Task 54 (Req All).

## Final reconciliation

- Relational: every table count + every FK + every unique constraint
  validated against the migration manifest digest.
- Binary: every `media_objects.final_object_path` resolves in the Blob
  store; checksum matches; archive replica present.
- Snapshots: every `tree_snapshots.checksum` matches its payload.
- Shares: every `share_links` either revoked or in an active
  `tree_key` whose members are still present.
- Audit: every committed mutation has an `audit_logs` row whose
  `entity_external_id` still resolves.
- Orphan inventory: `file_cleanup_jobs.completed_at IS NOT NULL` count
  matches `binary_replicas.REPLICATED` count.

## Repeated security/privacy/DR tests

- Penetration test report (independent).
- Dependency and container scans (zero critical/high).
- ASVS Level 2 evidence (closed in commit history).
- Privacy / retention tests (erasure dry-run + legal hold).
- DR drill evidence (snapshot / PITR / binary archive).

## ADR closure

- Each ADR is either `Accepted` or `Superseded`.
- Superseded ADRs reference the replacement ADR.
- Operational ownership transferred to the on-call team with signed
  handover.

## Handover evidence

- Cost baseline (compute, storage, transfer).
- SLO baseline (latency, error rate, availability).
- DR baseline (RPO, RTO, replication lag).
- Decommission evidence (legacy handlers removed, credentials revoked).
- Sign-off signatures from engineering, DBA, security, and operations.
