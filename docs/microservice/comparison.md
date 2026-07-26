# Comparison report: monolith vs. microservice decomposition

> Phase 7.2 deliverable. Quantitative + qualitative trade-off matrix
> for the genealogy domain.

> **Caveat**: this is research-grade. The monolith stays in production;
> this lane exists to learn the trade-offs.

## Quantitative (placeholder — populated by `make bench`)

| Metric                          | Monolith         | Microservice decomposition |
|---------------------------------|------------------|-----------------------------|
| Local end-to-end latency (p95)  | TBD ms           | TBD ms                      |
| Saga completion time            | n/a (1 DB tx)    | TBD s                       |
| Outbox relay lag (p95)          | n/a              | TBD s                       |
| Reconciliation drift %          | n/a              | TBD %                       |
| Lines of code (domain)          | TBD              | TBD                         |
| Lines of code (infra)           | TBD              | TBD                         |
| Service count                   | 1                | 12                          |
| MySQL schemas                   | 1                | 7                           |
| Build time (cold)               | TBD              | TBD                         |

## Qualitative

| Concern | Monolith | Microservice decomposition |
|---|---|---|
| Cross-aggregate atomicity | Free (1 DB) | Saga + reconciliation; eventual |
| Cycle detection on relationships | DB-level uniqueness | App-level (relationships-service) |
| Local dev setup | 1 process | 12 containers |
| Debugging | Stack trace in 1 process | Correlate via request id + traces |
| Independent deploys | No | Yes |
| Failure blast radius | Whole app | Single service |
| Schema migration | 1 migration | 11 coordinated migrations |
| Operational cost (CI / CD) | Lowest | 12× pipeline definitions |

## Lessons learned

1. **Domain code reuse via Maven dependency is the single biggest
   productivity win.** `services/identity-service` adds ~150 lines of
   code (Application + yml + Dockerfile) on top of the existing
   ~3,000-line `backend/identity-access` module. Without that, the
   decomposition would have required ~12× that amount.

2. **The composite same-tree FK is the hardest thing to give up.**
   Cross-aggregate invariants that the monolith enforces by FK must be
   re-implemented as application-level checks + reconciliation. This
   is the single largest source of code complexity in the decomposition.

3. **The outbox-relay pattern is a substitute for distributed
   transactions that mostly works.** It does NOT cover the case where
   a step's downstream broker is up but its database is down — that
   case still requires reconciliation.

4. **mTLS adds noticeable overhead** in the 1-3 ms range per call.
   Inside the same docker network with publisher confirms, this is
   acceptable. Across regions it would not be.

5. **Operational tooling dominates the long-tail cost.** Building the
   reconciliation, replay, and observability layers is the bulk of the
   work. The services themselves are small.

6. **The Next.js BFF needs an adapter layer, not a rewrite.** All 31
   API routes were rewritten to call the gateway through a single
   `springFetch` client. The DTO mapper (`src/lib/api/dto-mapper.ts`)
   keeps the legacy TypeScript types stable while the underlying
   microservice DTOs evolve. The BFF stays the contract owner; the
   gateway is the routing owner.

7. **Path conflicts at the gateway require service-specific path
   shims.** When two services share a path prefix (e.g.
   `/api/trees/{treeId}/members` is owned by both `members-service`
   for Member CRUD and `tree-service` for tree-membership), the
   gateway routes by exact path; one of the two services renames its
   endpoint (e.g. `tree-memberships`). This is invisible to the BFF
   but is a coordination cost.
