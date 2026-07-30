# Implementation Plan: Spring Boot Microservices Migration

## Status

**APPROVED — PHASE 1 ORDERED PLAN**

All tasks are intentionally unchecked. Execute in dependency order. No task in this document asserts completed implementation evidence.

## Tasks

- [x] 1. Supersede the architecture specification
  - [x] 1.1 Approve requirements, design, and standalone ADRs as one authoritative baseline
  - [x] 1.2 Archive or mark prior modular-monolith decisions as superseded
  - [x] 1.3 Establish requirement-to-design-to-task traceability
  - [x] 1.4 Run contradiction and unsupported-evidence scans over migration documentation
  - **Acceptance:** reviewers find one effective architecture: microservices, database per service, mandatory Kafka, Saga consistency, local authorization projections, async operations, and managed Kubernetes
  - _Requirements: 1-10_

- [x] 2. Freeze legacy behavior and publish the new contracts
  - [x] 2.1 Inventory every legacy route, frontend caller, domain rule, status, payload, header, cookie, and binary behavior
  - [x] 2.2 Build sanitized golden/property fixtures for identity, genealogy, recurrence, Vietnamese search, redaction, import, and export
  - [x] 2.3 Publish versioned V2 OpenAPI with `202/PENDING`, operation polling, ETag, revision/watermark, idempotency, and stable errors
  - [x] 2.4 Publish a frontend/PWA migration contract and remove immediate-success assumptions
  - [x] 2.5 Publish event catalog, ownership, schema compatibility, retention, partition keys, and PII classification
  - **Acceptance:** every frontend operation maps to OpenAPI; cross-service mutations contractually return an operation; event schemas pass compatibility and privacy gates; legacy exceptions are explicit
  - _Requirements: 2, 3, 7_

- [x] 2A. Verify the technology baseline and independent monorepo model
  - [x] 2A.1 Run a dependency-resolution and build spike for Java 25 LTS, Spring Boot 4.1.x, Maven Wrapper, and every production/test library in ADR-009
  - [x] 2A.2 If the spike fails, stop scaffolding and accept a superseding ADR selecting the nearest compatible Spring Boot GA before continuing
  - [x] 2A.3 Define per-service ownership of `pom.xml`, `mvnw`, pipeline, image, Flyway migrations, and Helm chart without a root Maven parent/reactor
  - [x] 2A.4 Scaffold one service and prove it builds without building another service
  - [x] 2A.5 Add dependency checks that reject cross-service source and domain-model imports
  - [x] 2A.6 Reproduce Flyway application and jOOQ generation against MySQL 8.4 Testcontainers
  - **Acceptance:** the complete matrix resolves and builds or a fallback ADR is accepted; one service builds independently; no root reactor or cross-service domain coupling exists; Flyway-driven jOOQ generation is reproducible
  - _Requirements: 1, 10.9, 11_

- [x] 3. Provision the managed platform foundation
  - [x] 3.1 Accept environment ADRs selecting cloud and managed MySQL, Kafka, KMS, and observability products before provisioning
  - [x] 3.2 Provision managed multi-AZ Kubernetes, Gateway/Ingress, namespaces, service accounts, NetworkPolicies, workload identity, and mTLS through reviewed Terraform
  - [x] 3.3 Provision managed MySQL 8.4 databases and managed Kafka-compatible clusters with Schema Registry, isolated credentials, ACLs, encryption, and DR configuration
  - [x] 3.4 Integrate managed secrets/KMS, cert rotation, per-service Helm charts, Argo CD reconciliation, and environment isolation
  - [x] 3.5 Install OpenTelemetry, metrics/log backends, dashboards, alerts, and trace propagation
  - [x] 3.6 Establish signed OCI, SBOM/provenance, same-digest promotion, HPA/KEDA, disruption, topology, and quota policies
  - **Acceptance:** required environment ADRs exist; Terraform/Helm validation passes; isolation and Kafka schema/ACL tests pass; Argo CD promotes the same signed image digest; platform failover, secret rotation, and telemetry smoke tests pass
  - _Requirements: 1.3-1.4, 3, 10, 11_

- [x] 4. Build and verify the Spring service template
  - [x] 4.1 Provide hexagonal structure using Spring MVC, Spring Security, Spring for Apache Kafka, gRPC Java, Resilience4j, and Micrometer/OpenTelemetry without shared business domain models
  - [x] 4.2 Add validation, idempotency, local audit/outbox, inbox/deduplication, retry/DLQ, replay, and owning-service Saga state-machine primitives without an external workflow engine
  - [x] 4.3 Add deadline-bound gRPC, REST, health, telemetry, and workload-identity defaults
  - [x] 4.4 Add Flyway schema authority, Spring Data JDBC aggregate persistence, jOOQ query/generation support, and exact MySQL 8.4 Testcontainers support
  - [x] 4.5 Add JUnit 5, AssertJ, Mockito, Testcontainers, and ArchUnit tests for database ownership, dependency cycles, synchronous call cycles, and unversioned events
  - [x] 4.6 Publish and version platform starters, OpenAPI contracts, event Protobuf artifacts, and gRPC Protobuf artifacts independently
  - **Acceptance:** template build and tests pass; duplicate/reordered messages are idempotent; event Protobuf compatibility passes; event and gRPC artifacts publish independently; architecture violations fail CI; no service credential can reach another database/topic
  - _Requirements: 1, 3, 10.9, 11_

- [x] 5. Implement Identity Service and transition bridge
  - [x] 5.1 Implement users, BCrypt compatibility/rehash, OAuth linkage, verification, lockout, opaque sessions, and revocation
  - [x] 5.2 Implement the server-side NextAuth bridge with short-lived asymmetric internal tokens and rotation/kill switch
  - [x] 5.3 Publish versioned `Identity*` events through local outbox
  - [x] 5.4 Build immutable identity extraction, idempotent load, final delta, reconciliation, writer freeze, and compare-and-set route authority
  - [x] 5.5 Rehearse identity cutover and rollback without production switch
  - **Acceptance:** golden auth contracts pass; duplicate identity races are constraint-safe; tokens do not persist in browser storage/logs; tests prove exactly one identity writer and exact reconciliation
  - _Requirements: 5, 9.4, 10.6_

- [x] 6. Implement Tree Access Service and authorization projection contract
  - [x] 6.1 Implement tree lifecycle, ownership, membership, roles, owner-admin invariant, and authoritative revision/epoch
  - [x] 6.2 Publish ordered tree/membership/revision events by `treeId`
  - [x] 6.3 Provide deadline-bound emergency authorization lookup
  - [x] 6.4 Build a reusable projection protocol/SDK for version, freshness, replay, and reconciliation without shared business models
  - [x] 6.5 Verify revocation propagation, deny-on-stale mutation, fail-closed sensitive reads, and owner immutability
  - **Acceptance:** role-by-operation and IDOR matrices pass; stale/missing projections never fail open; revocation objective and alerts pass under Kafka delay/outage
  - _Requirements: 6, 10.6_

- [x] 7. Implement Member Service
  - [x] 7.1 Implement profile CRUD, validation, tombstones, legacy avatar fallback, duplicate detection, and internal merge
  - [x] 7.2 Consume membership projection and expose member/tombstone events
  - [x] 7.3 Add migration loader, reconciliation endpoint, outbox/inbox, and independent deploy/rollback
  - [x] 7.4 Verify optimistic concurrency, parity corpus, stale authorization, replay, and database isolation
  - **Acceptance:** member golden/property tests pass; duplicates/replays do not duplicate effects; tombstoned members stay hidden; service independently rolls back
  - _Requirements: 1, 6, 7.1, 9_

- [x] 8. Implement Relationship Service
  - [x] 8.1 Implement relationship CRUD, canonical logical keys, local unique constraints, cycle validation, and genealogy algorithms
  - [x] 8.2 Serialize graph commands by `treeId` partition and aggregate version
  - [x] 8.3 Consume membership and member/tombstone projections
  - [x] 8.4 Add migration loader, reconciliation endpoint, outbox/inbox, and independent deploy/rollback
  - [x] 8.5 Verify concurrent cycle prevention, duplicate/reorder/gap handling, algorithm parity, and hot-partition behavior
  - **Acceptance:** adversarial graph tests preserve acyclicity and deterministic results without cross-service locks or transactions
  - _Requirements: 3, 6, 7.2, 9_

- [x] 9. Implement Event Service
  - [x] 9.1 Implement event CRUD, recurrence, deterministic ordering, and local member/media references
  - [x] 9.2 Consume membership, member, and media projections and reconcile dangling references
  - [x] 9.3 Add migration loader, reconciliation endpoint, outbox/inbox, and independent deploy/rollback
  - [x] 9.4 Verify leap-day parity, tombstone handling, stale projections, and event replay
  - **Acceptance:** event fixtures pass; cross-domain references use no foreign keys; projection lag cannot expose invalid active links
  - _Requirements: 1, 3, 6, 7.3, 9_

- [x] 10. Implement Media & Album Service and Blob controls
  - [x] 10.1 Implement album/media metadata, references, upload intents, quarantine, verification, mandatory scanning, promotion, thumbnails, and tombstones
  - [x] 10.2 Implement the official-JS Blob Control Gateway and exact-path signed browser data plane
  - [x] 10.3 Implement delayed cleanup, retention holds, orphan reconciliation, and independent encrypted binary replication
  - [x] 10.4 Publish media lifecycle/reference events without paths or capabilities
  - [x] 10.5 Add migration loader, reconciliation endpoint, outbox/inbox, and independent deploy/rollback
  - [ ] 10.6 Verify spoofed/corrupt/oversized/malicious files, scanner outage, capability abuse, association failure, cleanup, and archive restore
  - **Acceptance:** no active/public media is absent, unscanned, or unauthorized; failure injection leaves recoverable state; binary RPO/RTO drill passes
  - _Requirements: 3.7, 7.4, 10.6, 10.8_

- [x] 11. Implement Sharing Service
  - [x] 11.1 Implement hashed link creation/list/revocation/expiry and legacy-token migration support
  - [x] 11.2 Build versioned allowlisted public projections from tree/member/media events
  - [x] 11.3 Implement token-plus-media-ID access without arbitrary paths
  - [ ] 11.4 Verify synchronous revocation, forbidden fields, replay/rebuild, unknown/expired contracts, and stale projections
  - **Acceptance:** revocation takes effect immediately; automated scans find no private PII, owner/membership details, raw tokens, or Blob URLs
  - _Requirements: 7.5, 10.6_

- [x] 12. Implement Search & Reporting Service
  - [x] 12.1 Build MySQL projections for Vietnamese normalization, autocomplete, filters, statistics, and reports
  - [x] 12.2 Track per-domain watermarks and implement coherent revision barriers
  - [x] 12.3 Return watermark metadata and `202`/stable stale-projection errors when coherence cannot be reached
  - [x] 12.4 Add replay/rebuild, reconciliation, and independent deploy/rollback
  - [ ] 12.5 Verify parity, deterministic ordering, projection lag, rebuild, performance, and memory limits
  - **Acceptance:** golden search/report results pass at a stated watermark; p95 targets pass; no incoherent report is emitted
  - _Requirements: 2.6, 7.6, 8.6, 10.7_

- [ ] 13. Implement Saga and Operations foundation
  - [ ] 13.1 Implement operation API, durable state machines, transition guards, idempotency, and authorized operator actions
  - [ ] 13.2 Implement correlation/causation propagation, participant acknowledgements, deadlines, retries, DLQ, compensation, and manual review
  - [ ] 13.3 Build Audit & Operations projections without using them as business or authorization authority
  - [x] 13.4 Implement delete-member and delete-tree Sagas
    - [x] Owner-side Saga persistence (state, step, compensation snapshot) for both Member and Tree Access.
    - [x] Deterministic participant sequence with barrier (target aggregate version + epoch).
    - [x] Reply processor + outbox-staged compensation for terminal / failed transitions.
    - [x] Kafka command listener for delete-member participants (Relationship, Event, Media, Tree Access).
    - [x] Kafka command listener for delete-tree participants (Member, Relationship, Event, Media, Sharing, Search).
    - [x] Tree state machine extended with DELETE_FROZEN / PENDING_DELETION / DELETION_FINALIZED.
    - [x] Audit Ops lifecycle projection consumer (OperationStarted / OperationStateChanged).
     - [x] Compensation dispatch + DLQ / manual-review routing on participant failure.
     - [ ] Deadline scanner + retry scheduler (Task 13.6 dependency).
     - [ ] Fault-injection tests for participant outage / duplicate / reorder / broker restart (Task 13.6).
  - [ ] 13.5 Implement media-activation association Saga and cutover operation visibility
  - [ ] 13.6 Fault-inject every transition, participant outage, duplicate, reorder, broker outage, and orchestrator restart
  - **Acceptance:** no operation becomes invisibly stuck or falsely succeeds; compensation and operator retry are idempotent; target-revision completion is enforced
  - _Requirements: 2, 3, 4_

- [ ] 14. Implement Transfer Service and coordinated epochs
  - [ ] 14.1 Implement bounded GEDCOM/JSON/CSV preview and isolated parsing workers
  - [ ] 14.2 Implement staged append/replace import with cross-domain manifest validation
  - [ ] 14.3 Implement export/generated-artifact operations with authorization, cancellation, expiry, and private results
  - [ ] 14.4 Implement versioned snapshots and safety snapshots
  - [ ] 14.5 Implement import/restore freeze, distributed staging, verification, coordinated activation, compensation/rollback, binary reconciliation, and unfreeze
  - [ ] 14.6 Verify every pre/post-activation failure point and participant timeout
  - **Acceptance:** partial epochs never become visible; counts/hashes and participant watermarks prove completion; post-partial-activation failures enter explicit rollback or manual review
  - _Requirements: 4, 8_

- [ ] 15. Build the immutable migration and reconciliation pipeline
  - [ ] 15.1 Enumerate Blob objects read-only and preserve immutable raw source/manifests
  - [ ] 15.2 Transform service-specific manifests while preserving IDs/timestamps and explicit quarantine reasons
  - [ ] 15.3 Publish idempotent import commands and track each service loader/watermark
  - [ ] 15.4 Reconcile source accounting, IDs/counts, dangling references, graph hashes, projection watermarks, domain samples, and binary inventory
  - [ ] 15.5 Expose blocking discrepancy reports and cutover gates
  - **Acceptance:** `source = accepted + quarantined + approved duplicate`; reruns do not duplicate; every discrepancy has owner/disposition; zero unexplained dangling references remain
  - _Requirements: 9.1-9.3_

- [ ] 16. Update Gateway, frontend, and PWA semantics
  - [ ] 16.1 Preserve public routes while routing by global identity and tree cohort authority
  - [ ] 16.2 Implement operation polling and stable client-generated idempotency keys
  - [ ] 16.3 Version/clear private caches and offline queues by identity and contract
  - [ ] 16.4 Implement shadow reads only, normalized comparison, sampling, redaction, and kill switches
  - [ ] 16.5 Verify legacy/mixed/microservice routing and ambiguous retry behavior
  - **Acceptance:** all cross-service frontend mutations handle `202/PENDING`; no shadow mutation occurs; offline retries create one operation; identity changes clear private state
  - _Requirements: 2, 9.5_

- [ ] 17. Complete cross-cutting verification and security gates
  - [ ] 17.1 Run unit/property parity, MySQL 8.4 integration, Kafka replay/outage, Saga fault-injection, and differential contract suites
  - [ ] 17.2 Run IDOR/cross-tree, stale-role, CSRF, injection, token, event-PII, and Blob-capability tests
  - [ ] 17.3 Run typical/p95/maximum load and resilience tests; approve Saga completion and projection-lag SLOs
  - [ ] 17.4 Verify retention, legal hold, erasure, audit redaction, and supply-chain controls
  - [ ] 17.5 Complete independent penetration test and ASVS Level 2 evidence
  - **Acceptance:** all stated SLO/security/architecture gates pass; no unresolved critical/high finding lacks an approved time-bounded exception
  - _Requirements: 3-4, 6-10_

- [ ] 18. Rehearse migration, cutover, rollback, and disaster recovery
  - [ ] 18.1 Restore production-like source and execute full extraction/load/reconciliation
  - [ ] 18.2 Rehearse global identity then tree-cohort freeze/final-delta/CAS/unfreeze and rollback twice
  - [ ] 18.3 Inject MySQL loss, Kafka outage/replay, participant failure, projection rebuild, Blob/scanner failure, and partial epoch activation
  - [ ] 18.4 Verify ordered service restore, watermarks, reverse export, binary archive recovery, operator steps, RPO, and RTO
  - **Acceptance:** two consecutive rehearsals meet all gates; runbooks, owners, automated stops, and rollback evidence are approved
  - _Requirements: 4, 8-10_

- [ ] 19. Execute shadow and progressive production cutover
  - [ ] 19.1 Deploy signed immutable digests dark and verify platform/contracts/read-only shadowing
  - [ ] 19.2 Freeze, reconcile, and compare-and-set global identity authority
  - [ ] 19.3 Cut over internal/test then progressively larger tree cohorts
  - [ ] 19.4 Auto-stop on dual writer, security event, blocking discrepancy, SLO breach, consumer lag, or Saga failure threshold
  - [ ] 19.5 Verify each cohort's writer authority, participant watermarks, operation completion, and rollback readiness
  - **Acceptance:** exactly one writer exists for identity and every tree; all active APIs are microservice-owned; no structured production write reaches Blob JSON
  - _Requirements: 9.4-9.6, 10_

- [ ] 20. Stabilize, decommission, and audit
  - [ ] 20.1 Complete enhanced monitoring, defect closure, and post-cutover DR drills
  - [ ] 20.2 Wait for rollback-window expiry and zero legacy traffic
  - [ ] 20.3 Validate reverse export, then remove legacy structured readers/writers, cron, routes, projections, and credentials
  - [ ] 20.4 Reconcile final databases, Kafka/projections, binaries, snapshots, shares, audit, and orphan inventory
  - [ ] 20.5 Record cost/SLO/DR baselines and transfer signed operational ownership
  - **Acceptance:** no unexplained missing/duplicate record, broken active media, active legacy credential, stale writer, or unresolved critical/high vulnerability remains
  - _Requirements: 9.7, 10_
