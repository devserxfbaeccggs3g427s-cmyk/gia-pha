# Saga inventory

The 11 sagas defined in design.md §Sagas. Each saga has:
- An orchestrator service that owns the saga_log.
- A list of participant steps and their compensating actions.
- A runbook entry in `docs/microservice/sagas/<saga>.md`.

| Saga                  | Style        | Orchestrator         | Runbook |
|-----------------------|--------------|----------------------|---------|
| `RegisterSaga`        | Orchestration| `identity-service`   | `register.md` |
| `CreateTreeSaga`      | Orchestration| `tree-service`       | `create-tree.md` |
| `CreateMemberSaga`    | Orchestration| `members-service`    | `create-member.md` |
| `DeleteMemberSaga`    | Orchestration| `tree-service`       | `delete-member.md` |
| `CreateRelationshipSaga` | Orchestration | `relationships-service` | `create-relationship.md` |
| `CreateEventSaga`     | Orchestration| `events-service`     | `create-event.md` |
| `UploadMediaSaga`     | Choreography | n/a (events drive)   | `upload-media.md` |
| `DeleteMediaSaga`     | Orchestration| `media-metadata-service` | `delete-media.md` |
| `DeleteTreeSaga`      | Orchestration| `tree-service`       | `delete-tree.md` |
| `ImportSaga`          | Orchestration| `transfer-service`   | `import.md` |
| `RestoreSnapshotSaga` | Orchestration| `transfer-service`   | `restore-snapshot.md` |

## How a saga runs

1. The orchestrator reserves a `saga_log` row (state = STARTED).
2. For each step, the orchestrator invokes the participant via the
   shared `SagaStep` interface with a `CommandContext` carrying the
   `idempotencyKey`.
3. Participants consult their local idempotency registry (delegated to
   `audit-service` for cross-service idempotency) before applying.
4. On `OK`, the orchestrator advances. On `RETRYABLE_FAILURE`, the
   participant is retried with backoff. On `PERMANENT_FAILURE`, the
   orchestrator compensates in reverse order.

## Replay tooling

Operators can inspect and replay a saga from any state:

```bash
mysql -h <mysql-host> -u svc_transfer -p \
  -e "SELECT * FROM transfer.saga_log WHERE state IN ('COMPENSATING','FAILED') ORDER BY started_at DESC LIMIT 20;"
```

Phase 6.1 ships a small CLI in `tools/saga-replay/` (one file per
orchestrator service).
