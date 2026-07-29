# ADR-009: Standardized Java and Spring Technology Baseline

- **Status:** Accepted
- **Date:** 2026-07-27

## Context

Independent services need a reproducible runtime, persistence, telemetry, resilience, and test baseline. Java 25 and Spring Boot 4.1.x availability and library compatibility have not yet been demonstrated by repository artifacts.

## Decision

Use Java 25 LTS, Spring Boot 4.1.x, and Maven Wrapper as the initial baseline. Services use Spring MVC, Spring Security, Spring for Apache Kafka, gRPC Java, Resilience4j, Micrometer/OpenTelemetry, Spring Data JDBC, jOOQ, and Flyway. Tests use JUnit 5, AssertJ, Mockito, Testcontainers, and ArchUnit. Flyway is schema authority; Spring Data JDBC handles aggregate persistence and jOOQ handles explicit SQL and projections. Saga state machines remain in the owning service without an external workflow engine.

Before scaffolding, a dependency-resolution and build spike must verify the complete matrix. Failure blocks implementation until a superseding ADR selects the nearest compatible Spring Boot GA baseline.

## Consequences

The stack is standardized without claiming it already exists or that unresolved artifacts are available. Version changes require explicit architectural review, and generated jOOQ sources must remain reproducible from Flyway migrations.

## Verification

The spike resolves all production and test dependencies, compiles a minimal service, runs representative tests, applies Flyway to MySQL 8.4 Testcontainers, and reproduces jOOQ generation.
