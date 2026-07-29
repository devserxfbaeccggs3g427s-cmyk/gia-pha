# Plan: Update Service Structure to Traditional Layered Architecture

## Goal

Update the Spring Boot migration specification so every independently deployable microservice uses a traditional layered architecture organized **by feature**, replacing the current Hexagonal/Ports-and-Adapters service structure without changing the approved microservice boundaries, database ownership, Kafka/Saga contracts, deployment model, or technology baseline.

## Decisions

- Organize each service by feature rather than by global technical-layer packages.
- Within each feature, enforce the dependency direction `controller/consumer -> service -> repository`.
- Treat REST controllers, Kafka consumers, and gRPC endpoints as inbound/controller-layer components.
- Keep transport DTOs and mapping at the inbound boundary; domain/entity types must not depend on transport DTOs or controllers.
- The service layer owns use-case orchestration, transaction boundaries, authorization coordination, Saga logic, and calls to outbound integrations.
- Keep Spring Data JDBC and jOOQ behind the repository layer. Service classes must not use `DSLContext`, generated jOOQ types, or Spring Data repository implementations directly.
- Place Kafka producers and external gRPC clients in feature-local integration components invoked by the service layer. Do not recreate generic application/outbound ports merely to preserve Hexagonal terminology.
- Enforce these constraints with ArchUnit.

## Specification Updates

1. Update `.kiro/specs/spring-boot-backend-migration/requirements.md`:
   - Add acceptance criteria under Requirement 11 defining the feature-oriented layered structure and strict dependency direction.
   - Specify repository encapsulation of Spring Data JDBC/jOOQ and boundary-only transport DTOs.
   - Preserve all existing independent-build, no-shared-domain, database-per-service, Kafka, Saga, and contract requirements.
   - Remove or reword any requirement language that implies Ports and Adapters if found during the final consistency scan.

2. Update `.kiro/specs/spring-boot-backend-migration/design.md`:
   - Replace “Local Service Structure” and its Hexagonal flow with the approved feature-oriented layered model.
   - Include a representative package tree, for example:
     ```text
     com.<organization>.<service>/
       <feature>/
         controller/        # REST, gRPC endpoints, Kafka consumers
         dto/               # transport request/response/message DTOs
         mapper/            # boundary mappings
         service/           # use cases, transactions, orchestration
         repository/        # persistence boundary and implementations
         domain/            # entities, value objects, domain rules
         integration/       # Kafka producers, outbound gRPC clients
       config/
       shared/              # technical-only, no shared business domain
     ```
   - Document allowed dependencies and explicit prohibitions: controller-to-repository bypass, domain-to-transport dependencies, direct persistence-framework use from services, and cross-feature internals coupling.
   - Clarify that cross-feature collaboration occurs through feature service APIs or events, not another feature’s repository or internal DTOs.
   - Update reusable starter wording so it supplies technical infrastructure only and does not impose Hexagonal abstractions or contain business models.
   - Retain Flyway as schema authority, Spring Data JDBC for aggregate persistence, jOOQ for explicit/query SQL, and existing event/gRPC contract lifecycles.

3. Update `.kiro/specs/spring-boot-backend-migration/tasks.md`:
   - Rewrite task 4.1 from Hexagonal scaffolding to the feature-oriented layered template.
   - Expand architecture-test work in task 4.5 to enforce the layer dependency rules, repository encapsulation, transport DTO isolation, cross-feature boundaries, and existing cross-service isolation.
   - Adjust task 4 acceptance criteria to require a representative feature proving REST/Kafka/gRPC inbound paths, transactional service orchestration, repository-backed JDBC/jOOQ persistence, and outbound integration through the approved layers.
   - Replace any later task wording that assumes ports/adapters while preserving task order and requirement traceability.
   - Do not alter completion checkboxes except where the repository’s spec workflow explicitly requires reopening a completed task because its acceptance criteria changed; if reopened, state that the architecture-template task must be revalidated rather than implying prior implementation is absent.

4. Run a consistency review across the three spec files:
   - Ensure no effective statement still mandates Hexagonal Architecture, application ports, or outbound ports.
   - Ensure “layered” applies inside each service only and does not weaken bounded contexts or permit shared databases/domain code.
   - Ensure REST, Kafka, gRPC, Saga, outbox/inbox, authorization projections, and transaction ownership each have an unambiguous layer placement.
   - Verify requirement-to-design-to-task traceability remains intact and headings/status declarations do not contradict the revised architecture.
   - Check referenced ADR paths before changing the ADR index; no ADR files were found under the inspected spec directory, so do not invent or modify ADR documents without locating their actual source.

## Validation

- Search the updated spec for `hexagonal`, `port`, `adapter`, and related diagrams; any remaining occurrence must be historical/superseded context rather than a target requirement.
- Confirm Requirement 11 maps directly to the revised Local Service Structure and tasks 4.1/4.5 acceptance criteria.
- Confirm the package example and ArchUnit rules cover REST controllers, Kafka consumers/producers, gRPC endpoints/clients, services, repositories, DTOs/mappers, entities/domain code, and integrations.
- Confirm no change introduces a root Maven reactor, cross-service source dependency, shared business model, cross-service database access, or distributed transaction.

## Files to Modify During Implementation

- `.kiro/specs/spring-boot-backend-migration/requirements.md`
- `.kiro/specs/spring-boot-backend-migration/design.md`
- `.kiro/specs/spring-boot-backend-migration/tasks.md`

No source-code changes are part of this spec update.
