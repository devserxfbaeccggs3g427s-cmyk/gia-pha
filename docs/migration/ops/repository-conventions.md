# Repository and Transaction Conventions (Task 10)

Status: Active — governs every MySQL adapter in `backend/*/adapter/out/mysql`.

## 1. Tree-scoped repository APIs (10.1, 10.3)

- Every port method on tree content takes `long treeKey` as its first parameter (or
  receives it inside the aggregate); there is **no** repository method that can read or
  write tree content without a tree scope. Global scans are unrepresentable at the API
  level, not merely discouraged.
- Lookups by legacy identifier always use the pair `(tree_key, external_id)`, which is
  backed by a UNIQUE key on every content table.
- Aggregate repositories (`FamilyTreeRepository`, `MemberRepository`,
  `RelationshipRepository`, `EventRepository`, `MediaRepository`, `AlbumRepository`,
  `TreeMembershipRepository`) return domain records; read-only projections
  (`TreeStatsRepository` → `TreeStats`) live in `application/query` and never expose
  entity rows.
- Cross-tree references are rejected twice:
  1. schema level — association tables use composite same-tree FKs
     `(tree_key, entity_key)`, so a row crossing trees violates a FK;
  2. adapter level — `insertAll(treeKey, …)` throws `ConflictException`
     ("Batch insert crossing tree scope is forbidden") before touching the database.

## 2. Optimistic locking and batching (10.2)

- All `UPDATE` statements end with `AND version = :version` and set
  `version = version + 1`. Zero affected rows ⇒ `ConflictException`
  ("… was modified concurrently; retry with fresh state"); callers must re-read and
  retry, never blind-write.
- Guarded state transitions (media lifecycle) use compare-and-set on the guarded
  column itself: `SET status = :next WHERE … AND status = :expected`, returning
  whether the transition applied. The domain state machine
  (`MediaStatus.transitionTo`) validates the edge before SQL runs.
- Bulk writes use `NamedParameterJdbcTemplate.batchUpdate` (JDBC batch), single-row
  statements use `JdbcClient`. Generated keys come from `GeneratedKeyHolder` via
  `JdbcSupport.requiredKey`.
- Upserts use the MySQL 8.4 row-alias form
  (`VALUES (…) AS incoming ON DUPLICATE KEY UPDATE col = incoming.col`), never the
  deprecated `VALUES()` function.

## 3. Transaction templates and lock order (10.4)

`vn.giapha.tree.application.service.TreeTransactions` is the only sanctioned way to
open transactions around tree content:

| Template | Isolation | Usage |
| --- | --- | --- |
| `inTreeMutation(treeKey, work)` | READ_COMMITTED | All writes to tree content |
| `inMutation(work)` | READ_COMMITTED | Writes not bound to one tree (identity, ops tables) |
| `inReadOnlySnapshot(work)` | REPEATABLE_READ, read-only | Multi-query consistent reads (exports, statistics, snapshots) |

Stable lock order inside `inTreeMutation` (deadlock prevention):

1. `SELECT revision FROM family_trees WHERE tree_key = ? FOR UPDATE` — the tree row is
   always the first lock taken.
2. Entity rows are then touched in ascending primary-key order; batch inputs are
   processed in list order after the tree lock, so two writers on the same tree
   serialize at step 1 and cannot deadlock on entity rows.
3. `UPDATE family_trees SET revision = revision + 1` runs before commit; the revision
   is the tree-level change token (ETag source per ADR-015).
4. Audit and outbox rows are inserted inside the same transaction (Task 12), so a
   rollback leaves no partial state anywhere — no compensating logic exists or is
   permitted.

## 4. Query-plan review — index per query (10.5)

Every adapter query was mapped to its supporting index. Predicates are always
tree-first, matching the leftmost index column, so InnoDB range-scans one tree only.

| Query shape | Index used |
| --- | --- |
| `… WHERE tree_key = ? AND external_id = ?` (all content tables) | `uk_*_tree_external (tree_key, external_id)` |
| `… WHERE tree_key = ? AND <entity>_key = ?` | `uk_*_tree_<entity>` or PRIMARY |
| `members` page `ORDER BY member_key` / filters | `uk_members_tree_member`, `ix_members_tree_*` (6 tree-first secondary indexes) |
| `relationships` by member (`source_member_key = ? OR target_member_key = ?`) | `fk` composite indexes `(tree_key, source_member_key)` / `(tree_key, target_member_key)` — union of two range scans |
| relationship logical duplicate check | `uk_relationships_logical (tree_key, relation_type, pair_low_member_key, pair_high_member_key)` |
| `events` page `ORDER BY event_date, event_key` | `ix_events_tree_date (tree_key, event_date)` |
| `event_members` / `event_media` hydration `IN (:eventKeys)` | PRIMARY `(tree_key, event_key, *_key)` |
| `media_members` hydration / `removeMemberAssociations` | PRIMARY + `ix_media_members_member (tree_key, member_key)` |
| media page `ORDER BY uploaded_at DESC` | `tree_key` prefix range + filesort within one tree (bounded by LIMIT) |
| media by status (workers) | `ix_media_objects_tree_status`, `ix_media_objects_status_updated` |
| `album_media` by album / by media | `ix_album_media_album (tree_key, album_key)` / PRIMARY `(tree_key, media_key)` |
| `member_avatars` get/set | PRIMARY `(tree_key, member_key)` |
| `tree_memberships` by user (tree listing) | `ix_tree_memberships_user (user_key, tree_key)` |
| `TreeStats` COUNT subselects | index-only scans on each table's `(tree_key, …)` key |

Verification procedure (run against the docker-compose MySQL 8.4 from Task 8):

```sql
EXPLAIN FORMAT=TREE <adapter query with representative binds>;
```

Acceptance: no `Full table scan` node on any content table; every access path is
`Index lookup` / `Index range scan` rooted at a `tree_key`-leading index.

## 5. N+1 policy (10.5)

- Association collections are hydrated **per batch, not per row**: the event adapter
  loads `event_members` + `event_media` for a whole page in two `IN (:keys)` queries;
  the media adapter loads `media_members` + `album_media` the same way. A page of N
  aggregates costs a constant number of statements (1 count + 1 page + k association
  queries), independent of N.
- New adapters must follow the same `*Row` record + `hydrate(treeKey, rows)` pattern;
  issuing a query inside a per-row loop is a review-blocking defect.
- `MemberRepository.resolveKeys(treeKey, Set<externalIds>)` exists precisely so import
  and relationship code translates identifiers in one round trip.

## 6. Definition-of-Done evidence

- Cross-tree writes fail safely: composite FKs (schema) + batch scope guard (adapter).
- Primary query plans use intended indexes: mapping table above; `EXPLAIN` procedure
  documented for the Testcontainers/CI gate.
- Injected transaction failures leave no partial state: all writes (entities,
  associations, revision bump, audit, outbox) share one `TransactionTemplate`
  transaction; there are no post-commit writes in adapters.
- Automated tests for these properties are deferred by explicit project instruction
  (implementation first); the Testcontainers suites land with the task-group test
  passes.
