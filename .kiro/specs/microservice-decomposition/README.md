# Microservice Decomposition Spec

This spec is a parallel alternative to `../spring-boot-backend-migration/`. The original spec remains the authoritative production baseline. This spec exists for technical exploration of distributed-systems patterns in a personal genealogy project.

## Files

- `requirements.md` — What the decomposed system shall do.
- `design.md` — How it is structured and which trade-offs are accepted.
- `tasks.md` — Phased implementation plan.

## How to use this spec

1. Read `requirements.md` first to understand the accepted trade-offs.
2. Read `design.md` for the service map, event format, and saga inventory.
3. Follow `tasks.md` in phase order. Each task has a Definition of Done.
4. Capture lessons in `docs/microservice/notes.md` as you go.
5. After Phase 7, fill in `docs/microservice/comparison.md` and `docs/microservice/blog.md`.

## Relationship to the monolith spec

- Both specs share the same domain vocabulary.
- The monolith spec is the running production baseline.
- This spec is research-only; nothing here should be deployed to the family's real data.
- When this spec mentions "deploy", "production", or "operators", interpret as "local docker-compose environment" unless explicitly stated otherwise.

## When to stop

This spec is intentionally lossy compared to the monolith. Stop and return to the monolith spec if you observe:

- Loss of genealogy data (any kind).
- Cross-tree disclosure.
- An invariant that cannot be re-implemented without distributed transactions AND that invariant is required for correctness.
- Build complexity that exceeds the value of the research.

In any of these cases, document the failure mode in `docs/microservice/notes.md` and explicitly fold the lesson back into the monolith spec if it improves it.
