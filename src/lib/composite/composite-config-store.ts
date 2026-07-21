/**
 * CompositeConfigStore
 *
 * Low-level Blob storage operations for composite tree configuration.
 *
 * Responsibilities (matching spec Tasks 2.1 – 2.4):
 *
 *  2.1  Typed readers / writers for composite-config.json,
 *       composite-change-logs.json and disposable resolved manifests.
 *
 *  2.2  Atomic-enough config initialization: write config blob BEFORE
 *       acknowledging tree metadata so the caller can roll back tree
 *       metadata if this step fails.
 *
 *  2.3  Revision-aware (optimistic compare-and-swap) config mutations.
 *       Every mutation is written once under a unique immutable blob path and
 *       readers deterministically fold the log. Same-revision contenders are
 *       ordered by timestamp then mutation ID; only the first valid contender
 *       advances the revision and all others are retained as conflicts.
 *
 *  2.4  Deletion isolation: only composite-owned blobs are ever deleted
 *       (composite-config.json, composite-change-logs.json, and disposable
 *       resolved manifests).  Source tree blobs are never touched.
 *
 * Requirements covered: 1.2, 7.7, 11.1, 12.2, 12.7
 */

import { nanoid } from 'nanoid';
import { compositeTreeConfigSchema } from '@/data/schemas';
import type {
  CompositeAuditAction,
  CompositeAuditEntry,
  CompositeTreeConfig,
  SourceReference,
} from '@/data/types';
import { BLOB_PATHS, deleteBlobs, listBlobs } from '@/lib/blob/client';
import { appendCompositeMutation, readFoldedCompositeConfig } from './composite-mutation-log';
import { getCompositeAuditLog, getCompositeConfig } from '@/lib/blob/readers';
import { putCompositeAuditLog, putCompositeConfig } from '@/lib/blob/writers';

// ── Error type ────────────────────────────────────────────────────────────────

export type CompositeConfigErrorCode =
  | 'NOT_FOUND'
  | 'ALREADY_EXISTS'
  | 'STALE_CONFIG_REVISION'
  | 'INVALID_CONFIG';

export class CompositeConfigError extends Error {
  readonly code: CompositeConfigErrorCode;

  constructor(code: CompositeConfigErrorCode, message: string) {
    super(message);
    this.name = 'CompositeConfigError';
    this.code = code;
  }
}

// ── Mutator type ─────────────────────────────────────────────────────────────

/**
 * Pure function supplied by the caller of `mutateConfig`.
 *
 * Receives the current (validated) config and returns the fields that should
 * change.  The store fills in `treeId`, `schemaVersion`, `revision`,
 * `createdAt` and `updatedAt` automatically so the mutator need not touch them.
 */
export type ConfigMutator = (
  current: CompositeTreeConfig,
) => Omit<CompositeTreeConfig, 'treeId' | 'schemaVersion' | 'revision' | 'createdAt' | 'updatedAt'>;

// ── Audit context type ────────────────────────────────────────────────────────

export interface MutationAuditContext {
  /** Action discriminant recorded in the audit log entry. */
  action: CompositeAuditAction;
  /**
   * Structural snapshot of the config fields relevant to this action, taken
   * BEFORE the mutation is applied.  Must not contain living-person sensitive
   * data (names, phone, email, etc.).
   */
  previousData?: Record<string, unknown>;
  /** SourceReference most directly affected by this action, if any. */
  sourceReference?: SourceReference;
}

// ── CompositeConfigStore ──────────────────────────────────────────────────────

/**
 * Provides typed, revision-safe access to composite tree configuration blobs.
 *
 * All methods that write composite config do so exclusively to
 * `composite-config.json` and `composite-change-logs.json`.  No method in
 * this class touches members.json, relationships.json, events.json,
 * media-metadata.json or any other source-tree blob.
 */
export class CompositeConfigStore {
  // ── Reads ──────────────────────────────────────────────────────────────────

  /**
   * Read and Zod-parse the current config.
   * Returns `null` when the blob does not yet exist.
   */
  async getConfig(treeId: string): Promise<CompositeTreeConfig | null> {
    return getCompositeConfig(treeId);
  }

  /**
   * Read and Zod-parse the current config.
   * Throws `NOT_FOUND` when the blob does not yet exist.
   */
  async getConfigOrThrow(treeId: string): Promise<CompositeTreeConfig> {
    const config = await this.getConfig(treeId);
    if (config === null) {
      throw new CompositeConfigError(
        'NOT_FOUND',
        `No composite config found for tree "${treeId}". ` +
          'Ensure the composite tree was created before accessing its config.',
      );
    }
    return config;
  }

  /**
   * Read the composite audit log.
   * Returns an empty array when the blob does not yet exist.
   */
  async getAuditLog(treeId: string): Promise<CompositeAuditEntry[]> {
    return getCompositeAuditLog(treeId);
  }

  // ── Initialization (Task 2.2) ──────────────────────────────────────────────

  /**
   * Write an empty initial `CompositeTreeConfig` for a newly created composite
   * tree (Task 2.2).
   *
   * **Atomicity contract for the existing storage model:**
   * The caller MUST write the composite config blob BEFORE writing the tree
   * metadata to `trees.json`.  If this call throws, the caller must not add
   * the tree to `trees.json`.  Conversely, if `trees.json` cannot be updated
   * after this call succeeds, the caller must delete the config blob via
   * `deleteCompositeOwnedBlobs(treeId)` to avoid orphaned blobs.
   *
   * Throws `ALREADY_EXISTS` if a config blob is already present, preventing
   * accidental overwrite of an active composite.
   *
   * @param treeId  ID of the new composite tree (used as the blob key).
   * @param actorId User who created the composite tree (written to audit log).
   * @param now     Optional ISO timestamp override (useful in tests).
   */
  async initializeConfig(
    treeId: string,
    actorId: string,
    now?: string,
  ): Promise<CompositeTreeConfig> {
    const existing = await this.getConfig(treeId);
    if (existing !== null) {
      throw new CompositeConfigError(
        'ALREADY_EXISTS',
        `A composite config already exists for tree "${treeId}". ` +
          'Use mutateConfig to apply changes to an existing config.',
      );
    }

    const timestamp = now ?? new Date().toISOString();
    const initialConfig: CompositeTreeConfig = {
      treeId,
      schemaVersion: 1,
      revision: 0,
      sources: [],
      identityGroups: [],
      crossTreeRelationships: [],
      createdAt: timestamp,
      updatedAt: timestamp,
    };

    // Validate before writing so a bug in this code never persists a bad blob.
    const validated = compositeTreeConfigSchema.parse(initialConfig);

    // Write the config blob first (Task 2.2 ordering).
    await putCompositeConfig(treeId, validated);

    // Append a creation audit entry.
    await this.appendAuditEntry(treeId, {
      action: 'CONFIG_CREATED',
      actorId,
      revision: 0,
      newData: {
        treeId,
        schemaVersion: 1,
        revision: 0,
        sourceCount: 0,
        identityGroupCount: 0,
        crossTreeRelationshipCount: 0,
      },
      timestamp,
    });

    return validated;
  }

  // ── Optimistic compare-and-swap mutation (Task 2.3) ───────────────────────

  /**
   * Apply a mutation to the stored config with optimistic concurrency control
   * (Task 2.3, Requirement 12.7, Property 11).
   *
   * **Concurrency model:**
   * Vercel Blob does not support server-side conditional writes.  This method
   * therefore implements a *re-read-before-write* pattern:
   *
   *  1. Read the latest stored config (fresh, not cached).
   *  2. Compare `storedConfig.revision` with `expectedRevision`.
   *     - Mismatch → throw `STALE_CONFIG_REVISION`; no blob is modified.
   *  3. Apply the caller's `mutator` to produce the next config body.
   *  4. Validate the next config with the Zod schema; throw `INVALID_CONFIG`
   *     if invalid so a partial write never occurs.
   *  5. Write the new config blob (revision + 1).
   *  6. Append an audit entry (best-effort; audit lag is acceptable because
   *     the config write is the durable record of the mutation).
   *
   * If two concurrent requests both read revision N and both pass the revision
   * check before either completes their write, the last writer silently wins —
   * a known limitation of last-write-wins blob storage.  The spec acknowledges
   * this and requires the implementation not to *claim* strict atomic CAS.
   * For the typical request cadence (human-driven UI), sequential mutations
   * are the norm and stale revisions are detected reliably.
   *
   * @param treeId           Composite tree to mutate.
   * @param actorId          ID of the user performing the mutation.
   * @param expectedRevision Revision the caller last observed.
   * @param mutator          Pure function: current config → next config fields.
   * @param audit            Action type and optional context for the audit log.
   */
  async mutateConfig(
    treeId: string,
    actorId: string,
    expectedRevision: number,
    mutator: ConfigMutator,
    audit: MutationAuditContext,
  ): Promise<CompositeTreeConfig> {
    // Re-read immediately before write to minimise the stale detection window.
    const current = await this.getConfigOrThrow(treeId);

    if (current.revision !== expectedRevision) {
      throw new CompositeConfigError(
        'STALE_CONFIG_REVISION',
        `Composite config for tree "${treeId}" has revision ${current.revision} ` +
          `but the mutation expected revision ${expectedRevision}. ` +
          'Fetch the latest config and retry.',
      );
    }

    const now = new Date().toISOString();

    // Apply the caller's mutation logic.
    const patch = mutator(current);

    // Assemble the next config, overriding immutable / system fields.
    const next: CompositeTreeConfig = {
      ...current,
      ...patch,
      treeId: current.treeId,
      schemaVersion: current.schemaVersion,
      revision: current.revision + 1,
      createdAt: current.createdAt,
      updatedAt: now,
    };

    // Validate BEFORE writing; an invalid mutator must not corrupt the blob.
    const validationResult = compositeTreeConfigSchema.safeParse(next);
    if (!validationResult.success) {
      throw new CompositeConfigError(
        'INVALID_CONFIG',
        `The mutated config failed schema validation: ${validationResult.error.message}`,
      );
    }
    const validated = validationResult.data;

    const record = await appendCompositeMutation({ treeId, expectedRevision, actorId, action: audit.action, sourceReferences: audit.sourceReference ? [audit.sourceReference] : [], previousConfig: current, nextConfig: validated, createdAt: now });
    const folded = await readFoldedCompositeConfig(treeId);
    if (!folded?.accepted.some((item) => item.id === record.id)) {
      throw new CompositeConfigError('STALE_CONFIG_REVISION', `Mutation lost deterministic conflict resolution at revision ${expectedRevision}`);
    }

    // Compact: write the folded config as the new base so readers.getCompositeConfig()
    // always sees the latest state via a direct blob read (no re-fold needed).
    await putCompositeConfig(treeId, folded.config);

    // Append audit entry (best-effort after the durable write).
    await this.appendAuditEntry(treeId, {
      action: audit.action,
      actorId,
      revision: validated.revision,
      previousData: audit.previousData,
      newData: configSummaryForAudit(validated),
      sourceReference: audit.sourceReference,
      timestamp: now,
    });

    return validated;
  }

  // ── Deletion isolation (Task 2.4) ─────────────────────────────────────────

  /**
   * Delete every blob owned exclusively by this composite tree (Task 2.4,
   * Requirement 11.1).
   *
   * Deleted paths:
   *  - `data/trees/{treeId}/composite-config.json`
   *  - `data/trees/{treeId}/composite-change-logs.json`
   *  - All `cache/trees/{treeId}/resolved/{audienceHash}.json` manifests
   *
   * **Paths explicitly NOT touched:**
   *  - `data/trees/{treeId}/members.json`
   *  - `data/trees/{treeId}/relationships.json`
   *  - `data/trees/{treeId}/events.json`
   *  - `data/trees/{treeId}/media-metadata.json`
   *  - `data/trees/{treeId}/albums.json`
   *  - `data/trees/{treeId}/change-logs.json`
   *  - `data/trees/{treeId}/share-links.json`
   *  - Any media or backup blobs belonging to source trees
   *
   * Deletion is idempotent: missing blobs are silently skipped by Vercel Blob's
   * `del` API.
   */
  async deleteCompositeOwnedBlobs(treeId: string): Promise<void> {
    // Discover all disposable resolved manifests for this composite.
    const manifestBlobs = await listBlobs(BLOB_PATHS.compositeManifestPrefix(treeId));
    const manifestPaths = manifestBlobs.map((blob) => blob.pathname);

    const mutationBlobs = await listBlobs(BLOB_PATHS.compositeMutationPrefix(treeId));
    await deleteBlobs([
      BLOB_PATHS.compositeConfig(treeId),
      BLOB_PATHS.compositeChangeLogs(treeId),
      ...mutationBlobs.map((blob) => blob.pathname),
      ...manifestPaths,
    ]);
  }

  /**
   * Delete all resolved manifests (audience-keyed cache entries) for a tree
   * without touching the config or audit log.
   *
   * Useful for invalidating the resolved cache when a source tree changes or
   * when source permissions are revoked.
   */
  async deleteResolvedManifests(treeId: string): Promise<void> {
    const manifestBlobs = await listBlobs(BLOB_PATHS.compositeManifestPrefix(treeId));
    if (manifestBlobs.length === 0) return;
    await deleteBlobs(manifestBlobs.map((blob) => blob.pathname));
  }

  // ── Private helpers ───────────────────────────────────────────────────────

  /**
   * Append one entry to the composite audit log.
   *
   * The full log is re-read and re-written on every append.  This matches the
   * pattern used by the existing `ChangeLogService` and keeps the blob format
   * deterministic for tests.
   */
  private async appendAuditEntry(
    treeId: string,
    entry: Omit<CompositeAuditEntry, 'id' | 'compositeTreeId'>,
  ): Promise<void> {
    const existing = await getCompositeAuditLog(treeId);
    const newEntry: CompositeAuditEntry = {
      id: nanoid(),
      compositeTreeId: treeId,
      ...entry,
    };
    await putCompositeAuditLog(treeId, [...existing, newEntry]);
  }
}

// ── Module-level singleton ────────────────────────────────────────────────────

export const compositeConfigStore = new CompositeConfigStore();
export default compositeConfigStore;

// ── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Produce a structural summary of a config suitable for the audit log.
 *
 * Only counts and system-level identifiers are included; no living-person
 * sensitive data (member names, phone numbers, email addresses, etc.) ever
 * appears in this output, because `CompositeTreeConfig` does not contain
 * such fields.
 *
 * @internal Exported only for use in unit tests.
 */
export function configSummaryForAudit(config: CompositeTreeConfig): Record<string, unknown> {
  return {
    treeId: config.treeId,
    revision: config.revision,
    schemaVersion: config.schemaVersion,
    sourceCount: config.sources.length,
    sourceTreeIds: config.sources.map((s) => s.sourceTreeId),
    identityGroupCount: config.identityGroups.length,
    crossTreeRelationshipCount: config.crossTreeRelationships.length,
    publishedAt: config.publishedAt ?? null,
    updatedAt: config.updatedAt,
  };
}