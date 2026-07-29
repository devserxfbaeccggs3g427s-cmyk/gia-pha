# ADR-011: Monorepo with Independent Service Builds

- **Status:** Accepted
- **Date:** 2026-07-27

## Context

A monorepo simplifies discovery and coordinated change, but a root Maven reactor or cross-service source imports would undermine independent ownership, builds, releases, and rollback.

## Decision

Keep independently deployable services in one repository. Each service owns its `pom.xml`, Maven Wrapper, source, pipeline, image, Flyway migrations, and Helm chart. The repository root contains catalog and orchestration tooling only and is not a Maven parent/reactor build. Services do not import another service's source or domain model; platform starters and API, event, and gRPC contracts are versioned published artifacts.

## Consequences

Changed-service detection and artifact publication are required. Some coordinated changes span multiple independent releases, but no service build implicitly compiles or releases another service.

## Verification

CI builds one service without building another, rejects a root reactor and cross-service source/domain dependencies, verifies per-service wrappers and release assets, and validates independent artifact and image publication.
