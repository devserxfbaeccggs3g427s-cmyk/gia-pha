# ADR-008: Coordinated Epoch Import and Restore

- **Status:** Accepted
- **Date:** 2026-07-27

## Context

Import/replace and tree restore modify data owned by several services. One database transaction cannot provide atomic visibility, and exposing independently loaded domains would reveal partial or incoherent trees.

## Decision

Transfer Service orchestrates import and restore with coordinated epochs. The workflow parses and stages outside active state, validates a cross-domain manifest, freezes tree writes, distributes idempotent staged writes under an epoch, verifies participant counts/hashes, coordinates activation in every required service, reconciles binaries, then unfreezes.

Services expose only the active epoch. Failure before activation removes staging. Failure after partial activation invokes a defined rollback workflow; if safe compensation cannot be proven, the operation enters `MANUAL_REVIEW` while the tree remains frozen and partial state hidden. Restore first creates a safety snapshot.

## Consequences

Import/restore is eventually consistent operationally but has coordinated visibility. Every service needs staging, activation records, idempotent loaders, watermarks, retention, and rollback procedures. Completion can take longer and uses the async operation contract.

## Verification

Fault injection at every staging, verification, activation, rollback, binary-reconciliation, and unfreeze step must prove partial epochs are invisible. Counts, hashes, watermarks, safety snapshot, and operator evidence must gate success.
