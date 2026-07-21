# Implementation Plan: Composite Family Trees (MVP)

## Status

**READY FOR IMPLEMENTATION**

All tasks are intentionally unchecked. Implement in dependency order. The
advanced spec must not start until this plan is complete and its rollout is
stable.

## Tasks

- [x] 1. Add backward-compatible tree kinds and composite domain types
  - [x] 1.1 Add `FamilyTreeKind`, SourceReference, CompositeSource,
    CompositeIdentityGroup, CompositeRelationship and CompositeTreeConfig to
    `src/data/types.ts`
  - [x] 1.2 Add input and persisted-config Zod schemas, including source limits,
    reference uniqueness, scope rules and cross-tree relationship validation
  - [x] 1.3 Normalize missing `FamilyTree.kind` to `STANDALONE` at the read
    boundary and write explicit kind for all new trees
  - [x] 1.4 Add resolved DTO types instead of reusing or casting source Event
    and Media interfaces after IDs are remapped
  - [x]* 1.5 Add schema and backward-compatibility unit/property tests
  - _Requirements: 1.1, 1.2, 5.2, 12.1_

- [ ] 2. Implement composite Blob storage and optimistic config concurrency
  - [x] 2.1 Add blob paths and typed readers/writers for composite config,
    composite audit log and disposable resolved manifests
  - [x] 2.2 Create empty config atomically enough for the existing storage model;
    roll back tree metadata if config creation fails
  - [x] 2.3 Implement revision-aware config mutations; if Vercel Blob cannot
    provide atomic conditional overwrite, use append-only mutations plus a
    deterministic fold and document compaction
  - [x] 2.4 Ensure deletion removes only composite-owned blobs and never source
    paths
  - [ ]* 2.5 Test stale revision rejection and concurrent mutation preservation
  - _Requirements: 1.2, 7.7, 11.1, 12.2, 12.7_

- [ ] 3. Extend TreeService and RBAC for composite metadata
  - [x] 3.1 Support `kind=COMPOSITE` creation and preserve standalone as the
    default create behavior
  - [x] 3.2 Reject composite-as-source, self-reference and more than 20 sources
  - [x] 3.3 Add composite configuration permissions; only composite owner/Admin
    may mutate configuration in MVP
  - [x] 3.4 Implement independent source READ checks and source ADMIN consent for
    composite sharing
  - [ ]* 3.5 Add permission non-escalation and legacy RBAC regression tests
  - _Requirements: 1, 2.1, 7, 12.6_

- [x] 4. Implement source scope and preview algorithms
  - [x] 4.1 Implement FULL_TREE, DESCENDANTS and SELECTED_MEMBERS membership
    resolution using canonical parent→child edges
  - [x] 4.2 Implement optional direct-spouse context without unintended ancestry
    traversal
  - [x] 4.3 Filter relationships and compute Event/Media preview counts
  - [x] 4.4 Return warnings for invalid, missing or out-of-scope anchors
  - _Requirements: 2, 5.1_

- [ ] 5. Implement identity suggestion and confirmation
  - [x] 5.1 Reuse normalized Vietnamese matching and duplicate criteria to score
    cross-source candidate pairs
  - [x] 5.2 Create IdentityGroup CRUD with PROPOSED, CONFIRMED and REJECTED
    states
  - [x] 5.3 Enforce at most one confirmed group per SourceReference and require
    explicit reviewer metadata
  - [x] 5.4 Implement deterministic preferred-reference selection and conflict
    field detection without merging source data
  - [ ]* 5.5 Add identity uniqueness, reversibility and no-source-mutation tests
  - _Requirements: 3_

- [ ] 6. Implement deterministic CompositeResolver
  - [x] 6.1 Load authorized sources in parallel with bounded concurrency and
    build source version manifests
  - [x] 6.2 Implement deterministic VirtualMember IDs for grouped and ungrouped
    SourceReferences
  - [x] 6.3 Rewrite source relationships, events and media to virtual IDs
  - [x] 6.4 Aggregate provenance, remove self-edges and deduplicate logical edges
  - [x] 6.5 Add CrossTreeRelationships and validate referential integrity
  - [x] 6.6 Run whole-graph cycle detection and existing generation algorithm
  - [x] 6.7 Return partial results and sanitized placeholders for unavailable
    sources where appropriate
    normalization, DAG safety and source isolation
  - _Requirements: 4, 5, 10.1, 11.4, 12.3_

- [ ] 7. Introduce TreeDataProvider compatibility seam
  - [x] 7.1 Implement StandaloneTreeDataProvider using current readers with no
    domain behavior change
  - [x] 7.2 Implement CompositeTreeDataProvider using CompositeResolver
  - [x] 7.3 Route tree read endpoints and query hooks through the provider
  - [x] 7.4 Reject Member, Relationship, Event and Media mutations against a
    composite with `COMPOSITE_READ_ONLY`
  - [ ]* 7.5 Add contract tests comparing all standalone fixtures before and
    after provider adoption
  - _Requirements: 5, 6, 12.1_

- [ ] 8. Implement composition API routes and error contracts
  - [x] 8.1 Add config, source preview/CRUD and validation/publish routes
  - [x] 8.2 Add identity suggestion/group routes
  - [x] 8.3 Add cross-tree relationship create/delete routes
  - [x] 8.4 Map validation, permission, cycle and stale revision failures to
    stable HTTP status/error bodies
  - [x] 8.5 Record complete composite audit logs without private field leakage
  - [ ]* 8.6 Add API authorization and partial-write rollback tests
  - _Requirements: 2-7, 11.5_

- [ ] 9. Build composite creation and management UI
  - [x] 9.1 Add standalone/composite badges and the explicit composite create
    action to the trees page
  - [x] 9.2 Build resumable wizard for sources, scope preview, identity review,
    cross-tree links, validation and publication
  - [x] 9.3 Add conflict, stale source and unavailable source status panels
  - [x] 9.4 Add source badges, provenance and “Open in source tree” actions to
    Member details
  - [x] 9.5 Localize all UI/error strings in Vietnamese and English
  - [ ]* 9.6 Add accessibility, responsive and component interaction tests
  - _Requirements: 1.5, 3.7, 8_

- [ ] 10. Integrate existing read features with ResolvedTreeData
  - [x] 10.1 Render resolved graphs through TreeViewer in all supported modes
  - [x] 10.2 Run ancestry, generation and branch navigation on virtual IDs
  - [x] 10.3 Update search/filter results to return provenance
  - [x] 10.4 Deduplicate report/statistics counts by VirtualMember
  - [x] 10.5 Present remapped Event and Media data as read-only
  - [ ]* 10.6 Add the eight-branch end-to-end integration fixture and verify
    grandparents, eight children and every descendant appear exactly as
    configured
  - _Requirements: 5.6, 8, 9.1-9.3_

- [ ] 11. Implement composite export, import, backup and restore
  - [x] 11.1 Export flat resolved snapshots to GEDCOM/PDF/PNG/SVG with timestamp
    and source attribution where appropriate
  - [x] 11.2 Define versioned COMPOSITE_JSON containing configuration,
    provenance and source manifest without unauthorized source payloads
  - [x] 11.3 Import COMPOSITE_JSON as references and mark missing/forbidden
    sources unavailable
  - [x] 11.4 Backup and restore metadata/config/audit logs, revalidating all
    source permissions after restore
  - [ ]* 11.5 Add round-trip, deletion-isolation and unavailable-source tests
  - _Requirements: 9.4-9.6, 11_

- [ ] 12. Implement secure cache, offline behavior and privacy-safe sharing
  - [x] 12.1 Build audience-aware cache keys from source permissions, privacy
    policy, config revision and source versions
  - [x] 12.2 Never serve online cache before current permission validation
  - [x] 12.3 Mark offline data stale, disable config mutations and reauthorize on
    reconnect
  - [x] 12.4 Enforce source ADMIN sharing consent and redact sensitive fields of
    living people by default
  - [ ]* 12.5 Test permission revocation after cache creation, share-link
    redaction and offline reauthorization
  - _Requirements: 7.4-7.6, 10_

- [ ] 13. Optimize, observe and prepare rollout
  - [x] 13.1 Add concurrency limits, batched blob reads and resolver timing
    instrumentation
  - [ ] 13.2 Verify resolve metadata within 3 seconds for 1,000 VirtualMembers
    and 20 sources under the defined test environment
  - [ ] 13.3 Confirm TreeViewer lazy rendering and search/report performance
  - [x] 13.4 Add feature flag `COMPOSITE_TREES_ENABLED` and staged rollout
  - [x] 13.5 Monitor partial resolves, permission denials, cache hit rate, source
    read count and invalid configuration failures
  - _Requirements: 12.3-12.6_

- [ ] 14. Final MVP checkpoint
  - [ ] 14.1 Run typecheck, lint and the complete original test suite
  - [ ] 14.2 Run composite unit, property, API, integration, privacy and
    performance suites
  - [ ] 14.3 Verify manually: create root tree plus eight branch trees, compose,
    confirm boundary identities, edit one source and observe refreshed overview
  - [ ] 14.4 Verify source revocation, source deletion, composite deletion,
    backup/restore and share-link redaction
  - [ ] 14.5 Document operational limits and obtain explicit approval before
    starting `composite-family-trees-advanced`
  - _Requirements: All MVP requirements_
