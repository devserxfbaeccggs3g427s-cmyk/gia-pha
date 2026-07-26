# Decomposing a personal project: what I learned shipping 12 services for one family

> Phase 7.3 deliverable. Blog-style write-up distilling the lessons.

I maintain a small genealogy app for my family. It's a Next.js frontend
+ a Spring Boot backend that stores members, relationships, events,
media, and share links in one MySQL schema. It works. Last year I
spent a quarter exploring what would happen if I decomposed it into
12 microservices, one bounded context per service. This is what I
learned.

## What I actually built

Twelve services. Eleven backend services (identity, audit, tree,
members, relationships, events, media-metadata, binary-storage,
sharing, reporting, transfer) plus an API gateway. Each has its own
MySQL schema, its own Spring Boot fat-jar, its own Dockerfile, its
own deploy. They communicate over RabbitMQ.

I kept the monolith running in production. The decomposition lives
in a separate git branch and runs only on my laptop. This is critical:
the goal was to learn the trade-offs, not to migrate the family's
real data.

## The single biggest win: code reuse through Maven

The first thing I tried was to copy the monolith's `identity-access`
module into a new `identity-service`. After about an hour I had a
forest of duplicated code, three different versions of the user
model, and a migration that the monolith's tests didn't catch.

Then I tried a different approach: `services/identity-service/pom.xml`
depends on `vn.giapha:identity-access` — the **already-built monolith
JAR**. The service module adds three files: `Application.java`,
`application.yml`, `Dockerfile`. Total ~150 lines per service.

This worked. Domain code is identical. Tests pass for both the
monolith and the service. The decomposition lives at the deployment
boundary, not the code boundary. **If you're decomposing a Java
monolith, do this first.**

## What I gave up

The monolith enforced cross-aggregate invariants via composite
foreign keys. For example, a `relationships` row had a composite FK
`(tree_key, from_member_id)` that prevented linking across trees.
In the decomposition, no such FK exists. Instead, every consumer of
`MemberCreated` re-checks the tree scope before applying. The
`relationships-graph` projection is rebuilt from events. Every five
minutes, a reconciliation job compares projections against the source
of truth and emits drift events.

This works. But it works because this is a personal project with low
write volume. If I were doing this at scale, I'd add Saga-specific
event sourcing, optimistic concurrency tokens, and a real drift
detection pipeline. That's months of work, not days.

## The outbox is a poor substitute for distributed transactions

The outbox-relay pattern is the standard answer to "how do I publish
events from a transactional commit?" The answer is good: append an
outbox row in the same transaction, then a relay reads the outbox and
publishes to RabbitMQ. At-least-once, idempotent on the consumer side.

But: the outbox only covers the *commit* side. It does NOT cover the
case where the consumer is up but its database is down. It does NOT
cover the case where two consumers race on the same idempotency key.
It does NOT cover the case where the broker accepts the message but
then dies before persisting. For all those, you need reconciliation.

I built reconciliation. It was the largest single piece of code in
the decomposition. And it still has corner cases.

## The five-minute talk

If I had to distil this into a five-minute talk:

1. **Decompose at the deployment boundary, not the code boundary.**
   Reuse domain code via Maven dependency. Each service is ~150 lines.
2. **Outbox-as-a-service is good enough for 90% of cases.** The other
   10% need reconciliation.
3. **mTLS, signed events, and JWT-forward are non-negotiable.**
   Without them, the system is one breach away from data loss.
4. **Operational tooling dominates the long tail.** Reconciliation,
   replay, tracing, dashboards. Plan for it.
5. **For a personal project, the decomposition is overkill.** The
   monolith was right. I learned more about my own design decisions
   in three months of decomposition than in a year of monolith work,
   but I would not put this in production for my family.

## What I'd do differently

- Use a single outbox table with a `source` column instead of
  per-service tables. The per-service table adds migration overhead
  without giving meaningful isolation.
- Skip the `relationships-graph` projection and let consumers query
  `members-service` synchronously. The projection adds complexity for
  a query pattern that doesn't exist yet.
- Drop the choreographed `UploadMediaSaga`. It's harder to reason
  about than the orchestrator variant.

The full research artefacts — ADRs, runbooks, comparison report,
benchmark numbers — are in `docs/microservice/`. The microservices-
decomposition spec lives at `.kiro/specs/microservice-decomposition/`.
The monolith spec is the authoritative baseline.
