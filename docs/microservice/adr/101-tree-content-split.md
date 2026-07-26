# ADR-101: Tree-content split strategy

> Status: **Accepted** (per design.md §Tree-content split strategy).
> Phase: 0.1 (initial direction); decisive split implemented in Phase 2.

## Context

The monolith spec treats tree-content as one module with composite
same-tree FKs across every association table. The microservice spec's
decomposition map lists `tree-content-service` as a single row, but
Requirement 1.5 and the design.md note both call for an explicit
decision.

## Decision

**Strategy B** — split by aggregate (`members`, `relationships`,
`events`, `media-metadata`, `tree`). The user's goal is to learn the
trade-offs firsthand.

Sub-splits (each is a Maven module in `services/`):

| Service                   | Owns                                              |
|---------------------------|---------------------------------------------------|
| `tree-service`            | `family_trees`, `tree_memberships`, `tree_authority` |
| `members-service`         | `members`                                         |
| `relationships-service`   | `relationships`, `relationships_graph` (projection) |
| `events-service`          | `events`, `event_members`, `event_media`          |
| `media-metadata-service`  | `media_objects`, `media_members`, `member_avatars`, `albums`, `album_media` |

All five services connect to the same MySQL `tree_content` schema but
with different MySQL users (Requirement 13.5: no shared service
account). This isolates blast radius: a SQL mistake in `members-service`
can't accidentally drop `media_objects` because the user lacks DROP on
that table.

## Consequences

- Each tree-content sub-aggregate gets its own deployable.
- No longer a single tree-row lock. Concurrent graph mutations are
  serialised only within `relationships-service`.
- Cross-tree disclosure protection shifts from DB FK to application-
  level invariant re-checked on every consume.
- `DeleteMemberSaga` and `DeleteTreeSaga` exist precisely because of
  this decision.

## Alternatives considered

- **Strategy A** — keep tree-content as one service. Would shrink the
  saga count dramatically. Rejected because the lesson here is the
  trade-off, not the simplicity.
- **Strategy C** — per-feature micro-services. Rejected as overkill.
