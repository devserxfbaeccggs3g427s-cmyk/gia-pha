# PII Retention, Legal Hold and Erasure

Spec: spring-boot-backend-migration — Task 43A (Req 13.3, 15.14-15.16).

## Retention matrix (versioned)

| Data class | Legal basis | Retention | Erasure strategy |
| --- | --- | --- | --- |
| User account | consent | until deletion request + 30 d grace | soft-delete + binary cleanup + tombstone |
| Member record | consent | until deletion request + 30 d grace | pseudonymize + tombstone |
| Family tree | consent | until tree deletion + 30 d grace | drop + cleanup + tombstone |
| Audit logs (business) | legal obligation | 7 years | archive to cold storage, never mutate |
| Security audit | legal obligation | 2 years | archive to cold storage, never mutate |
| Share tokens | consent | until revoked + 24 h | revoke + binary cleanup |
| Sessions | consent | idle 30 m, abs 24 h | revoke + GC |
| Verification tokens | consent | 24 h | GC |
| Outbox events | legitimate interest | 7 days post-completion | archive |
| Snapshots | legitimate interest | 30 days | GC |
| Binary archive | legitimate interest | 30 days | GC |

## Erasure dependency preview

Before any user/member erasure, run `ErasurePlanner.preview(userId)` which
returns:

```
{
  "user": ["users", "oauth_accounts", "auth_sessions"],
  "trees_owned": ["family_trees (delete)", "binary cleanup jobs"],
  "trees_member": ["tree_memberships (drop)"],
  "media_owned": ["media_objects (tombstone)", "binary cleanup jobs"],
  "outstanding_share_links": ["share_links (revoke)"],
  "snapshots": ["preserve (legal hold)", "scrub personal fields"]
}
```

## Erasure execution

`ErasureService.execute(userId, strategy)` is idempotent and leased:
- Mark user `deleted_at`.
- Cascade memberships + tokens + sessions.
- Tombstone media, enqueue binary cleanup.
- Pseudonymize any data retained for legitimate interest.
- Emit audit row + outbox event so operators can replay / inspect.
- Return the dry-run report even when running for real so the same
  artifact can be attached to a privacy officer's review.

## Legal hold

`LegalHoldService.hold(aggregateId, reason)` blocks physical deletion:
- Cleanup workers skip objects on hold.
- Replays require a hold-override from a privacy officer.
- Holds expire on a fixed date or after explicit release.

## Dry-run mode

`/api/ops/erasure/{userId}?dryRun=true` returns the planned changes
without touching the database. Operations approves dry-run reports
before a real erasure.
