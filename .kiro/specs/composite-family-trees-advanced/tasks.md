# Implementation Plan: Composite Family Trees (Advanced / Deferred)

## Status

**DEFERRED**

Do not check off or start these tasks until
`../composite-family-trees/tasks.md` is complete and the activation gate in
`requirements.md` is approved. Tasks are ordered so every new write path is
introduced behind a safe, reversible read-only stage.

## Tasks

- [ ] 1. Establish advanced feature gates and job infrastructure
  - [ ] 1.1 Add flags `COMPOSITE_BRANCH_EXTRACTION_ENABLED`,
    `COMPOSITE_RECONCILIATION_ENABLED`, `COMPOSITE_MATERIALIZATION_ENABLED`
    and `COMPOSITE_NESTED_ENABLED`
  - [ ] 1.2 Add ExtractionJob model, blob paths, typed readers/writers and
    durable state transitions
  - [ ] 1.3 Implement short-request job coordinator with retry, cancellation,
    progress and idempotency keys
  - [ ] 1.4 Add job authorization that does not reveal source-private data
  - [ ]* 1.5 Test all state transitions, retries, cancellation and resumability
  - _Requirements: 8, 10_

- [ ] 2. Implement immutable origin and extraction manifests
  - [ ] 2.1 Add OriginReference and ExtractedEntityManifest storage separate from
    standalone domain interfaces
  - [ ] 2.2 Add SourceVersionManifest capture and checksum utilities
  - [ ] 2.3 Add origin lookup APIs and provenance UI links
  - [ ] 2.4 Add retention and cleanup rules for manifests without deleting
    source records or media
  - [ ]* 2.5 Add origin completeness and media isolation property tests
  - _Requirements: 1.4, 1.5, 2, 9_

- [ ] 3. Build branch extraction preview and quota preflight
  - [ ] 3.1 Implement descendant, descendant-with-spouse and selected-member
    scopes with ancestor-context choices
  - [ ] 3.2 Preview closure of members, relationships, events and media
  - [ ] 3.3 Apply privacy policy to copied living-member fields
  - [ ] 3.4 Estimate Blob bytes, operation count and target quota before job
    creation
  - [ ] 3.5 Detect source drift between preview and confirmation
  - [ ]* 3.6 Test closure, privacy, quota and source-drift behavior
  - _Requirements: 1, 10_

- [ ] 4. Implement resumable branch extraction job
  - [ ] 4.1 Create draft target tree and reserve target metadata
  - [ ] 4.2 Copy members in checksummed batches with deterministic old→new map
  - [ ] 4.3 Copy and rewrite canonical relationships, rejecting invalid closure
  - [ ] 4.4 Copy events/media metadata and binary media according to policy
  - [ ] 4.5 Persist per-batch origin manifests and progress checkpoints
  - [ ] 4.6 Verify checksums, counts, referential integrity and cycles
  - [ ] 4.7 Publish target only after complete validation; rollback or retain
    inaccessible draft on failure
  - [ ]* 4.8 Run pause/terminate/resume and retry idempotency integration tests
  - _Requirements: 1_

- [ ] 5. Add post-extraction identity and source-role workflow
  - [ ] 5.1 Propose boundary identity links using OriginReference
  - [ ] 5.2 Update composite source scopes to use old tree for context and new
    tree for descendants without duplicate nodes
  - [ ] 5.3 Add detach/reattach operations that preserve both trees
  - [ ] 5.4 Add UI badges for origin, branch authority and detached links
  - [ ]* 5.5 Test eight-branch split and overview repointing end to end
  - _Requirements: 2_

- [ ] 6. Implement source version manifests and diff generation
  - [ ] 6.1 Capture source collection hashes and tree updatedAt at resolve,
    extraction and reconciliation boundaries
  - [ ] 6.2 Implement idempotent entity matching by origin reference and source ID
  - [ ] 6.3 Produce field-level Member diffs and normalized relationship/event/
    media link diffs
  - [ ] 6.4 Represent permission changes and unavailable sources distinctly from
    deletes
  - [ ] 6.5 Add paginated diff APIs and redacted diff views
  - [ ]* 6.6 Add diff idempotency, version and permission property tests
  - _Requirements: 3_

- [ ] 7. Implement review, conflict and reconciliation records
  - [ ] 7.1 Add ChangeDiff, CompositeConflict, ReconciliationDecision and
    FieldDecision models/storage
  - [ ] 7.2 Add claim/assign/review status transitions with optimistic concurrency
  - [ ] 7.3 Detect same-field competing updates and relationship conflicts
  - [ ] 7.4 Require explicit decisions for every conflicting field
  - [ ] 7.5 Add immutable decision audit records and review UI
  - [ ]* 7.6 Test conflict grouping, decision completeness and replay safety
  - _Requirements: 4, 8, 9_

- [ ] 8. Implement authority policy and controlled source writes
  - [ ] 8.1 Add per-identity, per-field and relationship AuthorityPolicy
  - [ ] 8.2 Preview target source, changed fields and expected source version
  - [ ] 8.3 Route accepted decisions only to authorized standalone sources
  - [ ] 8.4 Add conditional source mutation and stale-write conflict responses
  - [ ] 8.5 Create PENDING_SOURCE_ACTION when authority is unavailable
  - [ ] 8.6 Record audit event in both composite and source trees
  - [ ]* 8.7 Test permission checks, stale versions and no fallback-source writes
  - _Requirements: 5_

- [ ] 9. Implement composite overlays with explicit lifecycle
  - [ ] 9.1 Define supported overlay fields and schema
  - [ ] 9.2 Display overlay provenance, reviewer, expiry and authority status
  - [ ] 9.3 Keep overlays separate from source domain records and mark them as
    overview-only
  - [ ] 9.4 Add expiry/review reminders and deterministic overlay precedence
  - [ ]* 9.5 Test overlay privacy and source refresh behavior
  - _Requirements: 4, 5_

- [ ] 10. Implement materialization to standalone snapshot
  - [ ] 10.1 Build preview for conflicts, redactions, unavailable sources and
    quota
  - [ ] 10.2 Implement preferred-source, non-empty and manual-decision policies
  - [ ] 10.3 Copy resolved members/relationships/events/media with new IDs and
    origin manifests
  - [ ] 10.4 Validate target using original standalone services
  - [ ] 10.5 Publish MaterializationManifest and target atomically enough for
    recovery; never mutate source/composite
  - [ ]* 10.6 Test materialization closure, round trip, failure recovery and
    deletion isolation
  - _Requirements: 6_

- [ ] 11. Add bounded nested composite support
  - [ ] 11.1 Add published-composite source validation and feature flag checks
  - [ ] 11.2 Build dependency graph and reject cycles before data reads
  - [ ] 11.3 Enforce depth 3, expanded-source 100 and concurrency limits
  - [ ] 11.4 Propagate privacy, provenance and stale/unavailable status
  - [ ] 11.5 Add leaf-source navigation in UI
  - [ ]* 11.6 Add nested DAG and permission non-escalation property tests
  - _Requirements: 7_

- [ ] 12. Add notifications, assignments and operational metrics
  - [ ] 12.1 Notify source changes, conflicts, unavailable sources and pending
    actions without sensitive preview data
  - [ ] 12.2 Add reviewer assignment and dashboard counters
  - [ ] 12.3 Add metrics for job durations, retry counts, p95 resolve latency,
    conflicts, pending writes, snapshot size and source failures
  - [ ] 12.4 Add retention jobs for completed diffs, decisions and manifests
  - [ ]* 12.5 Test notification privacy, idempotency and retention behavior
  - _Requirements: 8, 9, 10_

- [ ] 13. Advanced UI and export integration
  - [ ] 13.1 Add branch extraction wizard with progress and resumable status
  - [ ] 13.2 Add diff/conflict comparison UI with field provenance and decisions
  - [ ] 13.3 Add authority configuration and mutation confirmation screens
  - [ ] 13.4 Add materialization wizard and snapshot manifest view
  - [ ] 13.5 Extend export/share controls for origin/provenance privacy
  - [ ]* 13.6 Add Vietnamese/English accessibility and responsive tests
  - _Requirements: 1, 3, 4, 5, 6, 9_

- [ ] 14. Advanced rollout checkpoints
  - [ ] 14.1 Run full MVP regression suite before each flag activation
  - [ ] 14.2 Pilot extraction on small non-sensitive trees
  - [ ] 14.3 Pilot read-only diffs and manual decisions
  - [ ] 14.4 Enable authority writes for selected Admins only after stale-write
    and audit tests pass
  - [ ] 14.5 Enable materialization after quota, restore and privacy sign-off
  - [ ] 14.6 Enable nested composites last, with a kill switch to MVP behavior
  - [ ] 14.7 Review metrics and explicitly approve production rollout
  - _Requirements: All advanced requirements_
