/**
 * Tests for CompositeConfigStore (Task 2.5)
 *
 * Covers:
 *  - Stale revision rejection (Property 11, Requirement 12.7)
 *  - Successful sequential mutations with revision increment
 *  - Concurrent mutation simulation: second writer sees stale revision
 *  - initializeConfig creates a valid empty config and audit entry
 *  - ALREADY_EXISTS guard on double initialization
 *  - deleteCompositeOwnedBlobs: removes only composite blobs, never source paths
 *  - deleteResolvedManifests: removes only manifest cache entries
 *  - getConfigOrThrow: throws NOT_FOUND when config does not exist
 *  - Audit log is appended on each mutation
 *  - Schema-invalid mutator is rejected before writing
 */

import { describe, expect, it } from 'vitest';
import {
  CompositeConfigError,
  CompositeConfigStore,
  configSummaryForAudit,
} from '@/lib/composite/composite-config-store';
import { BLOB_PATHS, writeBlob } from '@/lib/blob/client';
import { getCompositeAuditLog, getCompositeConfig } from '@/lib/blob/readers';
import { putCompositeConfig } from '@/lib/blob/writers';
import { putMembers, putRelationships, putTrees } from '@/lib/blob/writers';
import { mockBlobStorage } from '../../utils/mock-blob-storage';
import { buildFamilyTree, buildMember, buildRelationship } from '../../utils/factories';
import type { CompositeTreeConfig } from '@/data/types';

// ── Helpers ───────────────────────────────────────────────────────────────────

const TREE_ID = 'composite-tree-1';
const ACTOR_ID = 'user-admin-1';
const FIXED_NOW = '2026-07-20T00:00:00.000Z';

function makeStore(): CompositeConfigStore {
  return new CompositeConfigStore();
}

async function seedInitializedConfig(
  treeId: string = TREE_ID,
  actorId: string = ACTOR_ID,
): Promise<CompositeTreeConfig> {
  const store = makeStore();
  return store.initializeConfig(treeId, actorId, FIXED_NOW);
}

// ── initializeConfig ──────────────────────────────────────────────────────────

describe('CompositeConfigStore.initializeConfig', () => {
  it('creates a valid empty config blob at the correct path', async () => {
    const store = makeStore();
    const config = await store.initializeConfig(TREE_ID, ACTOR_ID, FIXED_NOW);

    expect(config).toMatchObject<Partial<CompositeTreeConfig>>({
      treeId: TREE_ID,
      schemaVersion: 1,
      revision: 0,
      sources: [],
      identityGroups: [],
      crossTreeRelationships: [],
      createdAt: FIXED_NOW,
      updatedAt: FIXED_NOW,
    });

    // The blob must be persisted at the correct path.
    const stored = await getCompositeConfig(TREE_ID);
    expect(stored).toEqual(config);
  });

  it('appends a CONFIG_CREATED audit entry', async () => {
    const store = makeStore();
    await store.initializeConfig(TREE_ID, ACTOR_ID, FIXED_NOW);

    const log = await getCompositeAuditLog(TREE_ID);
    expect(log).toHaveLength(1);
    expect(log[0]).toMatchObject({
      compositeTreeId: TREE_ID,
      actorId: ACTOR_ID,
      action: 'CONFIG_CREATED',
      revision: 0,
    });
    expect(log[0].id).toBeTruthy();
    expect(log[0].timestamp).toBe(FIXED_NOW);
  });

  it('throws ALREADY_EXISTS when a config blob already exists', async () => {
    const store = makeStore();
    await store.initializeConfig(TREE_ID, ACTOR_ID, FIXED_NOW);

    // Second initialization must be rejected without modifying the stored config.
    await expect(store.initializeConfig(TREE_ID, ACTOR_ID)).rejects.toMatchObject(
      expect.objectContaining({ code: 'ALREADY_EXISTS' } satisfies Partial<CompositeConfigError>),
    );

    // Config blob must still be at revision 0.
    const config = await getCompositeConfig(TREE_ID);
    expect(config?.revision).toBe(0);
  });

  it('does not create any source-tree blobs (members, relationships, etc.)', async () => {
    const store = makeStore();
    await store.initializeConfig(TREE_ID, ACTOR_ID, FIXED_NOW);

    expect(mockBlobStorage.get(BLOB_PATHS.members(TREE_ID))).toBeUndefined();
    expect(mockBlobStorage.get(BLOB_PATHS.relationships(TREE_ID))).toBeUndefined();
    expect(mockBlobStorage.get(BLOB_PATHS.events(TREE_ID))).toBeUndefined();
    expect(mockBlobStorage.get(BLOB_PATHS.mediaMetadata(TREE_ID))).toBeUndefined();
    expect(mockBlobStorage.get(BLOB_PATHS.albums(TREE_ID))).toBeUndefined();
  });
});

// ── getConfigOrThrow ──────────────────────────────────────────────────────────

describe('CompositeConfigStore.getConfigOrThrow', () => {
  it('throws NOT_FOUND when no config blob exists', async () => {
    const store = makeStore();
    await expect(store.getConfigOrThrow('nonexistent-tree')).rejects.toMatchObject(
      expect.objectContaining({ code: 'NOT_FOUND' } satisfies Partial<CompositeConfigError>),
    );
  });

  it('returns the config when the blob exists', async () => {
    await seedInitializedConfig();
    const store = makeStore();
    const config = await store.getConfigOrThrow(TREE_ID);
    expect(config.revision).toBe(0);
    expect(config.treeId).toBe(TREE_ID);
  });
});

// ── mutateConfig – successful mutation ────────────────────────────────────────

describe('CompositeConfigStore.mutateConfig – successful mutation', () => {
  it('increments revision and persists the new config', async () => {
    await seedInitializedConfig();
    const store = makeStore();

    const result = await store.mutateConfig(
      TREE_ID,
      ACTOR_ID,
      0, // expectedRevision matches current revision 0
      (current) => ({
        sources: current.sources,
        identityGroups: current.identityGroups,
        crossTreeRelationships: current.crossTreeRelationships,
        publishedAt: FIXED_NOW,
      }),
      { action: 'CONFIG_PUBLISHED' },
    );

    expect(result.revision).toBe(1);
    expect(result.publishedAt).toBe(FIXED_NOW);
    expect(result.treeId).toBe(TREE_ID);

    // Persisted blob must reflect the new revision.
    const stored = await getCompositeConfig(TREE_ID);
    expect(stored?.revision).toBe(1);
    expect(stored?.publishedAt).toBe(FIXED_NOW);
  });

  it('carries `createdAt` through from the original config', async () => {
    await seedInitializedConfig();
    const store = makeStore();

    const result = await store.mutateConfig(
      TREE_ID,
      ACTOR_ID,
      0,
      (current) => ({
        sources: current.sources,
        identityGroups: current.identityGroups,
        crossTreeRelationships: current.crossTreeRelationships,
      }),
      { action: 'CONFIG_PUBLISHED' },
    );

    // createdAt must not change across mutations.
    expect(result.createdAt).toBe(FIXED_NOW);
  });

  it('appends a mutation audit entry to the log', async () => {
    await seedInitializedConfig();
    const store = makeStore();

    await store.mutateConfig(
      TREE_ID,
      ACTOR_ID,
      0,
      (current) => ({
        sources: current.sources,
        identityGroups: current.identityGroups,
        crossTreeRelationships: current.crossTreeRelationships,
        publishedAt: FIXED_NOW,
      }),
      { action: 'CONFIG_PUBLISHED' },
    );

    const log = await getCompositeAuditLog(TREE_ID);
    // First entry = CONFIG_CREATED, second = CONFIG_PUBLISHED
    expect(log).toHaveLength(2);
    expect(log[1]).toMatchObject({
      compositeTreeId: TREE_ID,
      actorId: ACTOR_ID,
      action: 'CONFIG_PUBLISHED',
      revision: 1,
    });
  });

  it('supports multiple sequential mutations with monotonically increasing revisions', async () => {
    await seedInitializedConfig();
    const store = makeStore();

    for (let expectedRevision = 0; expectedRevision < 3; expectedRevision++) {
      const result = await store.mutateConfig(
        TREE_ID,
        ACTOR_ID,
        expectedRevision,
        (current) => ({
          sources: current.sources,
          identityGroups: current.identityGroups,
          crossTreeRelationships: current.crossTreeRelationships,
        }),
        { action: 'CONFIG_PUBLISHED' },
      );
      expect(result.revision).toBe(expectedRevision + 1);
    }

    const stored = await getCompositeConfig(TREE_ID);
    expect(stored?.revision).toBe(3);

    const log = await getCompositeAuditLog(TREE_ID);
    // 1 creation + 3 mutations = 4 entries
    expect(log).toHaveLength(4);
  });
});

// ── mutateConfig – stale revision rejection (Property 11, Req 12.7) ───────────

describe('CompositeConfigStore.mutateConfig – stale revision rejection', () => {
  it('throws STALE_CONFIG_REVISION when expectedRevision is lower than current', async () => {
    await seedInitializedConfig();
    const store = makeStore();

    // Advance the stored config to revision 1.
    await store.mutateConfig(
      TREE_ID,
      ACTOR_ID,
      0,
      (current) => ({
        sources: current.sources,
        identityGroups: current.identityGroups,
        crossTreeRelationships: current.crossTreeRelationships,
      }),
      { action: 'CONFIG_PUBLISHED' },
    );

    // Now attempt a mutation with the old expected revision 0 → stale.
    await expect(
      store.mutateConfig(
        TREE_ID,
        ACTOR_ID,
        0, // stale: current is revision 1
        (current) => ({
          sources: current.sources,
          identityGroups: current.identityGroups,
          crossTreeRelationships: current.crossTreeRelationships,
          publishedAt: '2099-01-01T00:00:00.000Z',
        }),
        { action: 'CONFIG_PUBLISHED' },
      ),
    ).rejects.toMatchObject(
      expect.objectContaining({ code: 'STALE_CONFIG_REVISION' } satisfies Partial<CompositeConfigError>),
    );

    // The stored config must still be at revision 1 — the stale write must not
    // have modified any blob.
    const stored = await getCompositeConfig(TREE_ID);
    expect(stored?.revision).toBe(1);
    expect(stored?.publishedAt).toBeUndefined();
  });

  it('throws STALE_CONFIG_REVISION when expectedRevision is higher than current', async () => {
    await seedInitializedConfig();
    const store = makeStore();

    await expect(
      store.mutateConfig(
        TREE_ID,
        ACTOR_ID,
        99, // far ahead of actual revision 0
        (current) => ({
          sources: current.sources,
          identityGroups: current.identityGroups,
          crossTreeRelationships: current.crossTreeRelationships,
        }),
        { action: 'CONFIG_PUBLISHED' },
      ),
    ).rejects.toMatchObject(
      expect.objectContaining({ code: 'STALE_CONFIG_REVISION' } satisfies Partial<CompositeConfigError>),
    );
  });

  it('does NOT append an audit entry on stale revision rejection', async () => {
    await seedInitializedConfig();
    const store = makeStore();

    // First mutation succeeds (revision 0 → 1).
    await store.mutateConfig(
      TREE_ID,
      ACTOR_ID,
      0,
      (current) => ({
        sources: current.sources,
        identityGroups: current.identityGroups,
        crossTreeRelationships: current.crossTreeRelationships,
      }),
      { action: 'CONFIG_PUBLISHED' },
    );

    // Second mutation is stale (revision 0, but current is 1).
    await expect(
      store.mutateConfig(
        TREE_ID,
        ACTOR_ID,
        0,
        (current) => ({
          sources: current.sources,
          identityGroups: current.identityGroups,
          crossTreeRelationships: current.crossTreeRelationships,
        }),
        { action: 'CONFIG_PUBLISHED' },
      ),
    ).rejects.toHaveProperty('code', 'STALE_CONFIG_REVISION');

    // Audit log should have exactly: CONFIG_CREATED + CONFIG_PUBLISHED (2 entries).
    const log = await getCompositeAuditLog(TREE_ID);
    expect(log).toHaveLength(2);
  });
});

// ── mutateConfig – concurrent mutation simulation ─────────────────────────────

describe('CompositeConfigStore.mutateConfig – concurrent mutation simulation', () => {
  it('detects that a second concurrent write has already advanced the revision', async () => {
    await seedInitializedConfig();
    const storeA = makeStore();
    const storeB = makeStore();

    // Both readers observe revision 0 simultaneously.
    const configA = await storeA.getConfigOrThrow(TREE_ID);
    const configB = await storeB.getConfigOrThrow(TREE_ID);
    expect(configA.revision).toBe(0);
    expect(configB.revision).toBe(0);

    // Writer A commits revision 1 first.
    const resultA = await storeA.mutateConfig(
      TREE_ID,
      'user-a',
      configA.revision,
      (current) => ({
        sources: current.sources,
        identityGroups: current.identityGroups,
        crossTreeRelationships: current.crossTreeRelationships,
      }),
      { action: 'CONFIG_PUBLISHED' },
    );
    expect(resultA.revision).toBe(1);

    // Writer B attempts to commit with stale revision 0 → rejected.
    await expect(
      storeB.mutateConfig(
        TREE_ID,
        'user-b',
        configB.revision, // still 0, stale
        (current) => ({
          sources: current.sources,
          identityGroups: current.identityGroups,
          crossTreeRelationships: current.crossTreeRelationships,
          publishedAt: '2099-01-01T00:00:00.000Z',
        }),
        { action: 'CONFIG_PUBLISHED' },
      ),
    ).rejects.toHaveProperty('code', 'STALE_CONFIG_REVISION');

    // The stored config must reflect only writer A's successful write.
    const stored = await getCompositeConfig(TREE_ID);
    expect(stored?.revision).toBe(1);
    expect(stored?.publishedAt).toBeUndefined();
  });
});

// ── mutateConfig – schema validation ──────────────────────────────────────────

describe('CompositeConfigStore.mutateConfig – schema validation', () => {
  it('throws INVALID_CONFIG and does not write when the mutator produces an invalid config', async () => {
    await seedInitializedConfig();
    const store = makeStore();

    // Provide a mutator that introduces a duplicate source ID, violating the schema.
    const badSourceId = 'src-dup';
    const badSource = {
      id: badSourceId,
      sourceTreeId: 'source-tree-99',
      scope: 'FULL_TREE' as const,
      anchorMemberIds: [],
      selectedMemberIds: [],
      includeSpouses: false,
      includeEvents: true,
      includeMedia: true,
      allowCompositeSharing: false,
      shareLivingDetails: false,
      createdAt: FIXED_NOW,
      updatedAt: FIXED_NOW,
    };

    await expect(
      store.mutateConfig(
        TREE_ID,
        ACTOR_ID,
        0,
        () => ({
          // Two sources with the same id → schema violation
          sources: [badSource, { ...badSource, sourceTreeId: 'source-tree-100' }],
          identityGroups: [],
          crossTreeRelationships: [],
        }),
        { action: 'SOURCE_ADDED' },
      ),
    ).rejects.toMatchObject(
      expect.objectContaining({ code: 'INVALID_CONFIG' } satisfies Partial<CompositeConfigError>),
    );

    // The blob must still be at revision 0 — an invalid write must never persist.
    const stored = await getCompositeConfig(TREE_ID);
    expect(stored?.revision).toBe(0);
    expect(stored?.sources).toHaveLength(0);
  });
});

// ── deleteCompositeOwnedBlobs (Task 2.4) ──────────────────────────────────────

describe('CompositeConfigStore.deleteCompositeOwnedBlobs', () => {
  it('removes composite-config.json and composite-change-logs.json', async () => {
    await seedInitializedConfig();

    const store = makeStore();
    await store.deleteCompositeOwnedBlobs(TREE_ID);

    expect(mockBlobStorage.get(BLOB_PATHS.compositeConfig(TREE_ID))).toBeUndefined();
    expect(mockBlobStorage.get(BLOB_PATHS.compositeChangeLogs(TREE_ID))).toBeUndefined();
  });

  it('removes resolved manifest cache entries', async () => {
    await seedInitializedConfig();

    // Simulate two resolved manifests.
    const manifest1 = BLOB_PATHS.compositeManifest(TREE_ID, 'audience-hash-abc');
    const manifest2 = BLOB_PATHS.compositeManifest(TREE_ID, 'audience-hash-xyz');
    await writeBlob(manifest1, { stale: false });
    await writeBlob(manifest2, { stale: false });

    expect(mockBlobStorage.get(manifest1)).toBeDefined();
    expect(mockBlobStorage.get(manifest2)).toBeDefined();

    const store = makeStore();
    await store.deleteCompositeOwnedBlobs(TREE_ID);

    expect(mockBlobStorage.get(manifest1)).toBeUndefined();
    expect(mockBlobStorage.get(manifest2)).toBeUndefined();
  });

  it('never touches source-tree blobs (members, relationships, events, media)', async () => {
    await seedInitializedConfig();

    // Seed source-tree data that must survive the composite deletion.
    const sourceTreeId = 'source-tree-alpha';
    const member = buildMember({ treeId: sourceTreeId });
    const rel = buildRelationship({ treeId: sourceTreeId, sourceMemberId: member.id });
    await putMembers(sourceTreeId, [member]);
    await putRelationships(sourceTreeId, [rel]);

    // Also add metadata for the composite tree's own (non-composite) blobs —
    // the store must not delete these either.
    const sourceFamilyTree = buildFamilyTree({ id: sourceTreeId });
    await putTrees([sourceFamilyTree]);

    const store = makeStore();
    await store.deleteCompositeOwnedBlobs(TREE_ID);

    // Source tree data must still exist.
    expect(mockBlobStorage.get(BLOB_PATHS.members(sourceTreeId))).toBeDefined();
    expect(mockBlobStorage.get(BLOB_PATHS.relationships(sourceTreeId))).toBeDefined();
    expect(mockBlobStorage.get(BLOB_PATHS.trees())).toBeDefined();
  });

  it('is idempotent when composite blobs are already absent', async () => {
    // No blobs seeded. The deletion should succeed without throwing.
    const store = makeStore();
    await expect(store.deleteCompositeOwnedBlobs(TREE_ID)).resolves.toBeUndefined();
  });
});

// ── deleteResolvedManifests ───────────────────────────────────────────────────

describe('CompositeConfigStore.deleteResolvedManifests', () => {
  it('removes only manifest cache entries while keeping config and audit log', async () => {
    await seedInitializedConfig();

    const manifest = BLOB_PATHS.compositeManifest(TREE_ID, 'hash-123');
    await writeBlob(manifest, { stale: false });

    const store = makeStore();
    await store.deleteResolvedManifests(TREE_ID);

    expect(mockBlobStorage.get(manifest)).toBeUndefined();
    // Config and audit log must survive.
    expect(mockBlobStorage.get(BLOB_PATHS.compositeConfig(TREE_ID))).toBeDefined();
    expect(mockBlobStorage.get(BLOB_PATHS.compositeChangeLogs(TREE_ID))).toBeDefined();
  });

  it('is a no-op when no manifests exist', async () => {
    await seedInitializedConfig();
    const store = makeStore();
    // Must not throw.
    await expect(store.deleteResolvedManifests(TREE_ID)).resolves.toBeUndefined();
  });
});

// ── configSummaryForAudit ─────────────────────────────────────────────────────

describe('configSummaryForAudit', () => {
  it('produces a safe structural summary without personal data', () => {
    const config: CompositeTreeConfig = {
      treeId: 'tree-x',
      schemaVersion: 1,
      revision: 5,
      sources: [
        {
          id: 'src-1',
          sourceTreeId: 'tree-a',
          scope: 'FULL_TREE',
          anchorMemberIds: [],
          selectedMemberIds: [],
          includeSpouses: false,
          includeEvents: true,
          includeMedia: true,
          allowCompositeSharing: false,
          shareLivingDetails: false,
          createdAt: FIXED_NOW,
          updatedAt: FIXED_NOW,
        },
      ],
      identityGroups: [],
      crossTreeRelationships: [],
      publishedAt: FIXED_NOW,
      createdAt: FIXED_NOW,
      updatedAt: FIXED_NOW,
    };

    const summary = configSummaryForAudit(config);

    expect(summary).toEqual({
      treeId: 'tree-x',
      revision: 5,
      schemaVersion: 1,
      sourceCount: 1,
      sourceTreeIds: ['tree-a'],
      identityGroupCount: 0,
      crossTreeRelationshipCount: 0,
      publishedAt: FIXED_NOW,
      updatedAt: FIXED_NOW,
    });

    // The summary must not contain any field that could carry personal data.
    const summaryStr = JSON.stringify(summary);
    expect(summaryStr).not.toContain('anchorMemberIds');
    expect(summaryStr).not.toContain('selectedMemberIds');
    expect(summaryStr).not.toContain('preferredLabel');
  });
});