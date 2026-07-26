# ADR-011: MySQL search and Caffeine first; add infrastructure by evidence

Status: Accepted · 2026-07 · Requirements: 8, 12

## Context
Search is accent-insensitive scored matching over ≤ 20k members/tree with frozen scoring
weights; caching needs are read-mostly per-tree projections. Elasticsearch/Redis would be
two more systems to operate for a workload MySQL handles.

## Decision
- Search: persist a `search_normal` column (application-computed Vietnamese normalization,
  frozen pipeline) per member; prefix/contains matching via indexed `LIKE` on the
  normalized column; scoring in the application layer (identical to legacy weights).
- Caching: in-process Caffeine keyed by `(treeId, revision)` (see ADR-015); no external
  cache.
- Any additional infrastructure (OpenSearch, Redis) requires a measured SLO violation
  documented against NFR §2.

## Alternatives
- Elasticsearch — rejected now: operational cost; scoring must match legacy exactly,
  which BM25 does not.
- MySQL FULLTEXT — rejected: token/ngram behavior diverges from the frozen
  contains/prefix semantics with Vietnamese normalization.
- Redis cache — rejected now: single-deployable monolith gains nothing over Caffeine;
  revision keys make invalidation trivial.

## Consequences
Normalization has exactly one implementation (domain code) reused by writes (to compute
`search_normal`), search, and duplicate detection; index reviews in Task 10.5 cover the
`LIKE` plans.

## Failure modes
Multi-instance deployment makes Caffeine stale → revision check on read (cheap SELECT of
tree revision) bounds staleness to one request; scale-out beyond that is the documented
evidence to add Redis.

## Rollback
Both concerns sit behind ports (`MemberSearchPort`, `CachePort`); swapping
implementations is adapter-local.

## Single writer
`search_normal` is written only in the same transaction as the member row; no separate
indexer exists to drift.
