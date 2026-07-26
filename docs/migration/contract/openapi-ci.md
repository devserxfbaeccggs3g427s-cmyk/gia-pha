# OpenAPI CI Contract Gate (Task 11.4)

Requirement 1 freezes the legacy HTTP contract; Requirement 18.4 demands that
every contract change is caught mechanically, not by review vigilance. This
gate regenerates the Spring OpenAPI description on every CI run and diffs it
against the frozen legacy baseline.

## Inputs

| Artifact | Location | Role |
| --- | --- | --- |
| Legacy baseline | `docs/migration/contract/openapi-legacy-baseline.yaml` | Frozen contract (Task 1.3); never edited except through the exception process below |
| Golden fixtures | `docs/migration/contract/golden-fixtures/golden-fixtures.json` | Byte-level envelope/header evidence backing the baseline |
| Generated spec | `/v3/api-docs.yaml` from the running app (`contract` profile) | Current Spring surface |

## Generation

springdoc (`springdoc-openapi-starter-webmvc-api`, managed in `backend/pom.xml`)
is **disabled by default** (`springdoc.api-docs.enabled: false` in
`application.yml`). Only the `contract` profile
(`application-contract.yml` + `ContractDocsConfig`) enables it, restricted to
`/api/**` paths, with an unauthenticated chain that matches nothing but
`/v3/api-docs*`. The profile must never be set in a deployed environment.

CI steps (pipeline definition to be added with the CI infrastructure task; the
repository has no `.github/` workflows yet):

```bash
# 1. Throwaway database (same image as production line)
docker compose -f backend/docker-compose.yml up -d mysql

# 2. Boot the app with the contract profile
GIAPHA_DB_URL=... GIAPHA_DB_USERNAME=... GIAPHA_DB_PASSWORD=... \
  ./mvnw -pl app-bootstrap spring-boot:run \
  -Dspring-boot.run.profiles=contract &

# 3. Download the generated description once ready
curl --retry 30 --retry-connrefused -fsS \
  http://localhost:8080/v3/api-docs.yaml -o target/openapi-spring.yaml

# 4. Diff against the frozen baseline; breaking changes fail the build
oasdiff breaking \
  docs/migration/contract/openapi-legacy-baseline.yaml \
  target/openapi-spring.yaml \
  --fail-on ERR
```

`oasdiff changelog` (non-failing) is attached to the CI run as an artifact so
additive drift stays visible even when the gate passes.

## Gate policy

- **Breaking diff ⇒ red build.** Removed/renamed operations, narrowed enums,
  removed response fields, changed status codes, changed required-ness.
- **Additive diff ⇒ allowed but logged.** New V2 endpoints (Task 31) extend
  the surface; the compat surface itself must stay byte-stable.
- **Missing operations ⇒ expected during the strangler phase.** Until all
  routes are ported, the gate compares only operations present in the
  generated spec (`--match-path` filters per delivered task); the frontend
  call map (`frontend-call-map.md`) tracks remaining coverage.

## Exception process

A deliberate contract change (e.g. the public-share safe projection of
ADR-014) requires:

1. an entry in `docs/migration/contract/contract-exceptions.md` with owner,
   rationale, linked ADR and an **expiry date**;
2. the corresponding `oasdiff` rule added to the gate's ignore file;
3. sign-off recorded in the PR that changes the baseline.

Expired exceptions turn the gate red again — exceptions decay, they do not
accumulate (mirrors Task 39's "approved expiring exception" DoD).

## Relationship to golden contract tests

The OpenAPI diff checks *shape*; the golden fixtures check *bytes* (envelope
ordering, time format `uuuu-MM-dd'T'HH:mm:ss.SSS'Z'`, header casing). Golden
contract tests replay `golden-fixtures.json` against controllers as they are
delivered (deferred for now per project owner's instruction to prioritize
implementation over test execution).
