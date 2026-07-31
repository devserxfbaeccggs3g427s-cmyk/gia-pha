# Task 2A — Technology baseline and independent monorepo model

ADR-009 (Java 25 LTS, Spring Boot 4.1.x, Maven Wrapper) is the
authoritative initial baseline. The decision is gated on a
dependency-resolution and build spike that must resolve every
production and test library before any service scaffold lands.

## 2A.1 Compatibility gate

`platform/ci/baseline-spike.sh` runs the spike. It:

1. Boots a temporary Maven Wrapper at the version pinned in
   `.mvn/wrapper/maven-wrapper.properties`.
2. Resolves the artifact matrix from
   `platform/starters/bom/pom.xml` (Java 25, Spring Boot 4.1.x,
   Spring for Apache Kafka, gRPC Java, Resilience4j,
   Micrometer/OpenTelemetry, Flyway, jOOQ, Spring Data JDBC).
3. Applies Flyway migrations to a MySQL 8.4 Testcontainers instance.
4. Reproduces jOOQ code generation from the migrated schema.
5. Writes `target/baseline-spike.json` with the resolved versions and
   the spike verdict.

If the spike fails, implementation stops and a superseding ADR is
required to pick the nearest compatible Spring Boot GA baseline.

## 2A.2 Per-service ownership

Each service under `services/<service>/` owns:

- `pom.xml`
- `mvnw`, `mvnw.cmd`, `.mvn/wrapper/`
- `src/` and `db/migration/`
- `deploy/helm/`
- `pipeline/` (GitHub Actions)
- `Dockerfile` and `cosign` provenance config
- `OWNERS` (CODEOWNERS-style ownership file)

The repository root does **not** contain a Maven parent/reactor build
that compiles all services. `tooling/affected-services.sh` enumerates
changed services for the changed-service pipeline; CI builds only
those.

## 2A.3 No root reactor

A repository-wide test enforces the absence of a root reactor:

- `tooling/architecture-scan/src/main/java/com/familya/governance/scan/NoRootReactorTest.java`
  inspects every `pom.xml` and rejects `<modules>` outside a service.
- `platform/ci/ci.yml` runs the test on every PR.

## 2A.4 One service builds independently

`platform/ci/independent-build.sh` removes every other service from
the working tree and proves that a service builds, packages, and runs
Flyway without the others present.

## 2A.5 Cross-service source and domain-model rejection

- `tooling/architecture-scan/src/main/java/com/familya/governance/scan/CrossServiceImportTest.java`
  rejects any source path under `services/<a>/src/**` that imports
  `services.<b>`.
- Domain-model isolation is enforced by the same scanner plus a
  separate `archunit` test in every service (`archunit/ArchitectureTest.java`).

## 2A.6 Flyway + jOOQ against MySQL 8.4 Testcontainers

`platform/ci/flyway-jooq-testcontainers.sh`:

1. Starts `mysql:8.4` Testcontainers.
2. Runs `mvn -pl services/identity-service -am flyway:migrate` against it.
3. Runs `mvn -pl services/identity-service -am jooq-codegen:generate`.
4. Asserts the generated sources under
   `services/identity-service/target/generated-sources/jooq/**` exist
   and compile.

## Acceptance

- Baseline spike JSON is committed at `platform/ci/baseline-spike.json`.
- One service builds without any other service present.
- No root reactor exists; no service imports another service.
- Flyway + jOOQ runs reproducibly against MySQL 8.4 Testcontainers.
