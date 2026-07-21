import { nanoid } from 'nanoid';
import {
  addSourceInputSchema,
  compositeRelationshipKey,
  crossTreeRelationshipInputSchema,
  identityGroupInputSchema,
  sourceReferenceKey,
  sourceScopeInputSchema,
  updateSourceInputSchema
} from '@/data/schemas';
import type {
  AddSourceInput,
  CrossTreeRelationshipInput,
  IdentityGroupInput,
  SourceScopeInput,
  UpdateSourceInput
} from '@/data/schemas';
import type {
  CompositeIdentityGroup,
  CompositeRelationship,
  CompositeSource,
  CompositeTreeConfig,
  CompositeAuditAction,
  FamilyTree,
  FamilyTreeKind,
  SourcePreview,
  SourceReference
} from '@/data/types';
import { getCompositeConfig, getEvents, getMediaMetadata, getMembers, getRelationships, getTrees } from '@/lib/blob/readers';
import { resolveSourceScope } from '@/lib/algorithms/source-scope';
import { getCompositeAuditLog } from '@/lib/blob/readers';
import { putCompositeAuditLog, putCompositeConfig } from '@/lib/blob/writers';
import {
  canAccessTree,
  requireCompositeAdminPermission,
  requireSourceAdminConsent,
  requireSourceReadPermission
} from '@/lib/auth/rbac';

export type CompositeConfigErrorCode =
  | 'NOT_FOUND'
  | 'INVALID_INPUT'
  | 'NOT_COMPOSITE_TREE'
  | 'COMPOSITE_READ_ONLY'
  | 'SOURCE_NOT_FOUND'
  | 'SOURCE_NOT_STANDALONE'
  | 'SOURCE_FORBIDDEN'
  | 'SOURCE_UNAVAILABLE'
  | 'SOURCE_LIMIT_EXCEEDED'
  | 'INVALID_SCOPE'
  | 'REFERENCE_OUT_OF_SCOPE'
  | 'IDENTITY_REFERENCE_CONFLICT'
  | 'DUPLICATE_RELATIONSHIP'
  | 'RELATIONSHIP_CYCLE'
  | 'INVALID_COMPOSITE_CONFIG'
  | 'STALE_CONFIG_REVISION'
  | 'COMPOSITE_NOT_PUBLISHED';

export class CompositeConfigError extends Error {
  constructor(
    public readonly code: CompositeConfigErrorCode,
    message: string,
    public readonly details?: Record<string, unknown>
  ) {
    super(message);
    this.name = 'CompositeConfigError';
  }
}

const MAX_SOURCES = 20;

export class CompositeConfigService {
  /**
   * Returns the validated composite config for a tree. Throws if the tree does
   * not exist, is not a composite, or if the config blob is missing.
   */
  async getConfig(treeId: string): Promise<CompositeTreeConfig> {
    assertIdentifier(treeId, 'treeId');
    await this.#requireCompositeTree(treeId);
    const config = await getCompositeConfig(treeId);
    if (!config) {
      throw new CompositeConfigError(
        'INVALID_COMPOSITE_CONFIG',
        `Composite config not found for tree "${treeId}"`
      );
    }
    return config;
  }

  async previewSource(treeId: string, actorId: string, input: unknown): Promise<SourcePreview> {
    assertIdentifier(treeId, 'treeId');
    assertIdentifier(actorId, 'actorId');
    await requireCompositeAdminPermission(treeId, actorId);
    const parsed: SourceScopeInput = sourceScopeInputSchema.parse(input);
    await requireSourceReadPermission(parsed.sourceTreeId, actorId);

    const [members, relationships, events, mediaMetadata] = await Promise.all([
      getMembers(parsed.sourceTreeId),
      getRelationships(parsed.sourceTreeId),
      getEvents(parsed.sourceTreeId),
      getMediaMetadata(parsed.sourceTreeId)
    ]);
    const result = resolveSourceScope(members, relationships, events, mediaMetadata, parsed);

    return {
      sourceTreeId: parsed.sourceTreeId,
      memberCount: result.members.length,
      relationshipCount: result.relationships.length,
      eventCount: result.events.length,
      mediaCount: result.mediaMetadata.length,
      warnings: result.warnings
    };
  }

  /**
   * Adds a new source to the composite config.
   *
   * Guards (Task 3.2, 3.3, 3.4):
   * - Composite actor must be ADMIN of the composite tree.
   * - Source must be STANDALONE (not COMPOSITE). Nested composites are MVP out-of-scope.
   * - Source cannot be the composite tree itself (self-reference).
   * - Actor must have READ on the source tree at the time of addition.
   * - If `allowCompositeSharing` is true, actor must also be ADMIN of the source tree.
   * - Enforces the 20-source limit (Requirement 12.6).
   * - Enforces optimistic concurrency via `expectedRevision` (Requirement 12.7).
   */
  async addSource(
    treeId: string,
    actorId: string,
    input: unknown,
    expectedRevision: number
  ): Promise<CompositeTreeConfig> {
    assertIdentifier(treeId, 'treeId');
    assertIdentifier(actorId, 'actorId');

    await requireCompositeAdminPermission(treeId, actorId);
    const parsed: AddSourceInput = addSourceInputSchema.parse(input);

    if (parsed.sourceTreeId === treeId) {
      throw new CompositeConfigError(
        'INVALID_COMPOSITE_CONFIG',
        'A composite tree cannot reference itself as a source'
      );
    }

    await requireSourceReadPermission(parsed.sourceTreeId, actorId);

    if (parsed.allowCompositeSharing) {
      await requireSourceAdminConsent(parsed.sourceTreeId, actorId);
    }

    const config = await this.#readConfigWithRevisionCheck(treeId, expectedRevision);

    if (config.sources.length >= MAX_SOURCES) {
      throw new CompositeConfigError(
        'SOURCE_LIMIT_EXCEEDED',
        `Cannot add more than ${MAX_SOURCES} sources to a composite tree`
      );
    }

    if (config.sources.some((s) => s.sourceTreeId === parsed.sourceTreeId)) {
      throw new CompositeConfigError(
        'INVALID_COMPOSITE_CONFIG',
        `Source tree "${parsed.sourceTreeId}" is already included in this composite`
      );
    }

    const now = new Date().toISOString();
    const sourceTree = parsed.allowCompositeSharing
      ? (await getTrees()).find((tree) => tree.id === parsed.sourceTreeId)
      : undefined;
    const newSource: CompositeSource = {
      id: nanoid(),
      sourceTreeId: parsed.sourceTreeId,
      scope: parsed.scope,
      anchorMemberIds: parsed.anchorMemberIds,
      selectedMemberIds: parsed.selectedMemberIds,
      includeSpouses: parsed.includeSpouses,
      includeEvents: parsed.includeEvents,
      includeMedia: parsed.includeMedia,
      allowCompositeSharing: parsed.allowCompositeSharing,
      shareLivingDetails: parsed.shareLivingDetails,
      sourceVersion: sourceTree?.updatedAt,
      ...(parsed.allowCompositeSharing ? { sharingConsentedBy: actorId, sharingConsentedAt: now, sharingConsentSourceVersion: sourceTree?.updatedAt } : {}),
      ...(parsed.preferredLabel !== undefined ? { preferredLabel: parsed.preferredLabel } : {}),
      createdAt: now,
      updatedAt: now
    };

    const updated: CompositeTreeConfig = {
      ...config,
      sources: [...config.sources, newSource],
      revision: config.revision + 1,
      updatedAt: now,
    };
    await this.#persistMutation(treeId, actorId, config, updated, 'SOURCE_ADDED');
    return updated;
  }

  /**
   * Updates scope and policy settings of an existing source entry.
   *
   * Guards:
   * - Composite actor must be ADMIN.
   * - If `allowCompositeSharing` is being enabled, actor must be source ADMIN.
   * - Optimistic concurrency check on `expectedRevision`.
   */
  async updateSource(
    treeId: string,
    actorId: string,
    sourceId: string,
    input: unknown,
    expectedRevision: number
  ): Promise<CompositeTreeConfig> {
    assertIdentifier(treeId, 'treeId');
    assertIdentifier(actorId, 'actorId');
    assertIdentifier(sourceId, 'sourceId');

    await requireCompositeAdminPermission(treeId, actorId);
    const parsed: UpdateSourceInput = updateSourceInputSchema.parse(input);

    const config = await this.#readConfigWithRevisionCheck(treeId, expectedRevision);
    const sourceIndex = config.sources.findIndex((s) => s.id === sourceId);
    if (sourceIndex < 0) {
      throw new CompositeConfigError('NOT_FOUND', `Source "${sourceId}" not found in composite`);
    }

    const existing = config.sources[sourceIndex];

    if (parsed.allowCompositeSharing && !existing.allowCompositeSharing) {
      await requireSourceAdminConsent(existing.sourceTreeId, actorId);
    }

    const now = new Date().toISOString();
    const enablingSharing = Boolean(parsed.allowCompositeSharing && !existing.allowCompositeSharing);
    const sourceTree = enablingSharing ? (await getTrees()).find((tree) => tree.id === existing.sourceTreeId) : undefined;
    const updatedSource: CompositeSource = {
      ...existing,
      scope: parsed.scope ?? existing.scope,
      anchorMemberIds: parsed.anchorMemberIds ?? existing.anchorMemberIds,
      selectedMemberIds: parsed.selectedMemberIds ?? existing.selectedMemberIds,
      includeSpouses: parsed.includeSpouses ?? existing.includeSpouses,
      includeEvents: parsed.includeEvents ?? existing.includeEvents,
      includeMedia: parsed.includeMedia ?? existing.includeMedia,
      allowCompositeSharing: parsed.allowCompositeSharing ?? existing.allowCompositeSharing,
      shareLivingDetails: parsed.shareLivingDetails ?? existing.shareLivingDetails,
      ...(enablingSharing ? { sharingConsentedBy: actorId, sharingConsentedAt: now, sharingConsentSourceVersion: sourceTree?.updatedAt } : {}),
      ...((parsed.allowCompositeSharing === false) ? { sharingConsentedBy: undefined, sharingConsentedAt: undefined, sharingConsentSourceVersion: undefined } : {}),
      preferredLabel: parsed.preferredLabel ?? existing.preferredLabel,
      updatedAt: now
    };

    const updated = this.#bumpRevision(config, now);
    updated.sources = [
      ...config.sources.slice(0, sourceIndex),
      updatedSource,
      ...config.sources.slice(sourceIndex + 1)
    ];
    await this.#persistMutation(treeId, actorId, config, updated, 'SOURCE_UPDATED');
    return updated;
  }

  /**
   * Removes a source from the composite config.
   *
   * Cascades removal of identity groups and cross-tree relationships that
   * reference the removed source tree. The operation only writes to
   * `composite-config.json`; it never touches any source tree blob
   * (Requirement 2.6).
   */
  async removeSource(
    treeId: string,
    actorId: string,
    sourceId: string,
    expectedRevision: number
  ): Promise<CompositeTreeConfig> {
    assertIdentifier(treeId, 'treeId');
    assertIdentifier(actorId, 'actorId');
    assertIdentifier(sourceId, 'sourceId');

    await requireCompositeAdminPermission(treeId, actorId);
    const config = await this.#readConfigWithRevisionCheck(treeId, expectedRevision);

    const sourceIndex = config.sources.findIndex((s) => s.id === sourceId);
    if (sourceIndex < 0) {
      throw new CompositeConfigError('NOT_FOUND', `Source "${sourceId}" not found in composite`);
    }

    const removedTreeId = config.sources[sourceIndex].sourceTreeId;
    const now = new Date().toISOString();
    const updated = this.#bumpRevision(config, now);
    updated.sources = config.sources.filter((s) => s.id !== sourceId);

    updated.identityGroups = config.identityGroups
      .map((group) => {
        const remainingRefs = group.references.filter((ref) => ref.treeId !== removedTreeId);
        if (remainingRefs.length === group.references.length) return group;
        if (remainingRefs.length < 2) return null;
        return {
          ...group,
          references: remainingRefs,
          preferredReference:
            group.preferredReference?.treeId === removedTreeId
              ? undefined
              : group.preferredReference,
          status: 'PROPOSED' as const,
          reviewedBy: undefined,
          reviewedAt: undefined
        };
      })
      .filter((group): group is NonNullable<typeof group> => group !== null);
    updated.crossTreeRelationships = config.crossTreeRelationships.filter(
      (rel) => rel.source.treeId !== removedTreeId && rel.target.treeId !== removedTreeId
    );

    await this.#persistMutation(treeId, actorId, config, updated, 'SOURCE_REMOVED');
    return updated;
  }

  /**
   * Creates or fully replaces an identity group within a composite.
   *
   * Guards (Task 3.3, Requirement 3.5):
   * - Composite actor must be ADMIN.
   * - A SourceReference may belong to at most one CONFIRMED group per composite.
   * - All referenced source trees must be configured sources.
   */
  async upsertIdentityGroup(
    treeId: string,
    actorId: string,
    groupId: string | null,
    input: unknown,
    expectedRevision: number
  ): Promise<CompositeTreeConfig> {
    assertIdentifier(treeId, 'treeId');
    assertIdentifier(actorId, 'actorId');

    await requireCompositeAdminPermission(treeId, actorId);
    const now = new Date().toISOString();
    const normalizedInput = input && typeof input === 'object' && !Array.isArray(input)
      ? {
          ...input,
          ...(('status' in input && input.status === 'CONFIRMED')
            ? {
                reviewedBy: ('reviewedBy' in input && typeof input.reviewedBy === 'string') ? input.reviewedBy : actorId,
                reviewedAt: ('reviewedAt' in input && typeof input.reviewedAt === 'string') ? input.reviewedAt : now
              }
            : {})
        }
      : input;
    const parsed: IdentityGroupInput = identityGroupInputSchema.parse(normalizedInput);
    const config = await this.#readConfigWithRevisionCheck(treeId, expectedRevision);

    const configuredSourceTreeIds = new Set(config.sources.map((s) => s.sourceTreeId));
    for (const ref of parsed.references) {
      if (!configuredSourceTreeIds.has(ref.treeId)) {
        throw new CompositeConfigError(
          'INVALID_COMPOSITE_CONFIG',
          `Identity reference tree "${ref.treeId}" is not a configured source`
        );
      }
    }

    if (parsed.status === 'CONFIRMED') {
      const newReferenceKeys = new Set(parsed.references.map(sourceReferenceKey));
      const conflictingGroup = config.identityGroups.find(
        (group) =>
          group.id !== groupId &&
          group.status === 'CONFIRMED' &&
          group.references.some((ref) => newReferenceKeys.has(sourceReferenceKey(ref)))
      );
      if (conflictingGroup) {
        throw new CompositeConfigError(
          'IDENTITY_REFERENCE_CONFLICT',
          'A SourceReference can belong to at most one confirmed identity group'
        );
      }
    }

    const existingIndex = groupId
      ? config.identityGroups.findIndex((g) => g.id === groupId)
      : -1;
    if (groupId && existingIndex < 0) {
      throw new CompositeConfigError('NOT_FOUND', `Identity group "${groupId}" not found`);
    }

    const upsertedGroup: CompositeIdentityGroup = {
      id: groupId ?? nanoid(),
      references: parsed.references,
      status: parsed.status,
      ...(parsed.preferredReference !== undefined ? { preferredReference: parsed.preferredReference } : {}),
      reviewedBy: parsed.status === 'CONFIRMED' ? (parsed.reviewedBy ?? actorId) : parsed.reviewedBy,
      reviewedAt: parsed.status === 'CONFIRMED' ? (parsed.reviewedAt ?? now) : parsed.reviewedAt,
      ...(parsed.reason !== undefined ? { reason: parsed.reason } : {}),
      createdAt: existingIndex >= 0
        ? config.identityGroups[existingIndex].createdAt
        : now,
      updatedAt: now
    };

    const updated = this.#bumpRevision(config, now);
    if (existingIndex >= 0) {
      updated.identityGroups = [
        ...config.identityGroups.slice(0, existingIndex),
        upsertedGroup,
        ...config.identityGroups.slice(existingIndex + 1)
      ];
    } else {
      updated.identityGroups = [...config.identityGroups, upsertedGroup];
    }

    await this.#persistMutation(treeId, actorId, config, updated, 'IDENTITY_GROUP_UPSERTED', parsed.references);
    return updated;
  }

  /**
   * Removes an identity group. The next resolve will restore separate virtual
   * nodes per source reference without touching any source data
   * (Requirement 3.6).
   */
  async removeIdentityGroup(
    treeId: string,
    actorId: string,
    groupId: string,
    expectedRevision: number
  ): Promise<CompositeTreeConfig> {
    assertIdentifier(treeId, 'treeId');
    assertIdentifier(actorId, 'actorId');
    assertIdentifier(groupId, 'groupId');

    await requireCompositeAdminPermission(treeId, actorId);
    const config = await this.#readConfigWithRevisionCheck(treeId, expectedRevision);

    if (!config.identityGroups.some((g) => g.id === groupId)) {
      throw new CompositeConfigError('NOT_FOUND', `Identity group "${groupId}" not found`);
    }

    const now = new Date().toISOString();
    const updated = this.#bumpRevision(config, now);
    const removed = config.identityGroups.find((g) => g.id === groupId)!;
    updated.identityGroups = config.identityGroups.filter((g) => g.id !== groupId);
    await this.#persistMutation(treeId, actorId, config, updated, 'IDENTITY_GROUP_REMOVED', removed.references);
    return updated;
  }

  /**
   * Adds a cross-tree relationship between two source references belonging to
   * different active source trees.
   *
   * Guards (Task 3.3, Requirement 4.3):
   * - Composite actor must be ADMIN.
   * - Both endpoints must belong to different configured source trees.
   * - Duplicate canonical relationships are rejected.
   */
  async createCrossTreeRelationship(
    treeId: string,
    actorId: string,
    input: unknown,
    expectedRevision: number
  ): Promise<CompositeTreeConfig> {
    assertIdentifier(treeId, 'treeId');
    assertIdentifier(actorId, 'actorId');

    await requireCompositeAdminPermission(treeId, actorId);
    const parsed: CrossTreeRelationshipInput = crossTreeRelationshipInputSchema.parse(input);
    const config = await this.#readConfigWithRevisionCheck(treeId, expectedRevision);

    const configuredSourceTreeIds = new Set(config.sources.map((s) => s.sourceTreeId));

    if (parsed.source.treeId === parsed.target.treeId) {
      throw new CompositeConfigError('INVALID_COMPOSITE_CONFIG', 'Cross-tree relationship endpoints must belong to different sources');
    }
    if (!configuredSourceTreeIds.has(parsed.source.treeId)) {
      throw new CompositeConfigError(
        'INVALID_COMPOSITE_CONFIG',
        `Relationship source tree "${parsed.source.treeId}" is not a configured source`
      );
    }
    if (!configuredSourceTreeIds.has(parsed.target.treeId)) {
      throw new CompositeConfigError(
        'INVALID_COMPOSITE_CONFIG',
        `Relationship target tree "${parsed.target.treeId}" is not a configured source`
      );
    }

    const newKey = compositeRelationshipKey(parsed);
    const duplicate = config.crossTreeRelationships.some(
      (rel) => compositeRelationshipKey(rel) === newKey
    );
    if (duplicate) {
      throw new CompositeConfigError(
        'DUPLICATE_RELATIONSHIP',
        'An equivalent cross-tree relationship already exists'
      );
    }

    await this.#assertReferencesInScope(config, [parsed.source, parsed.target]);

    const now = new Date().toISOString();
    const relationship: CompositeRelationship = {
      id: nanoid(),
      source: parsed.source,
      target: parsed.target,
      type: parsed.type,
      ...(parsed.customType !== undefined ? { customType: parsed.customType } : {}),
      ...(parsed.marriageDate !== undefined ? { marriageDate: parsed.marriageDate } : {}),
      ...(parsed.divorceDate !== undefined ? { divorceDate: parsed.divorceDate } : {}),
      ...(parsed.marriageStatus !== undefined ? { marriageStatus: parsed.marriageStatus } : {}),
      createdBy: actorId,
      createdAt: now
    };

    const updated = this.#bumpRevision(config, now);
    updated.crossTreeRelationships = [...config.crossTreeRelationships, relationship];
    await this.#persistMutation(treeId, actorId, config, updated, 'CROSS_TREE_RELATIONSHIP_CREATED', [parsed.source, parsed.target]);
    return updated;
  }

  /**
   * Removes a cross-tree relationship. Only the config entry is deleted; no
   * source tree blob is modified (Requirement 4.6).
   */
  async deleteCrossTreeRelationship(
    treeId: string,
    actorId: string,
    relationshipId: string,
    expectedRevision: number
  ): Promise<CompositeTreeConfig> {
    assertIdentifier(treeId, 'treeId');
    assertIdentifier(actorId, 'actorId');
    assertIdentifier(relationshipId, 'relationshipId');

    await requireCompositeAdminPermission(treeId, actorId);
    const config = await this.#readConfigWithRevisionCheck(treeId, expectedRevision);

    if (!config.crossTreeRelationships.some((r) => r.id === relationshipId)) {
      throw new CompositeConfigError(
        'NOT_FOUND',
        `Cross-tree relationship "${relationshipId}" not found`
      );
    }

    const removed = config.crossTreeRelationships.find((r) => r.id === relationshipId)!;
    const now = new Date().toISOString();
    const updated = this.#bumpRevision(config, now);
    updated.crossTreeRelationships = config.crossTreeRelationships.filter(
      (r) => r.id !== relationshipId
    );
    await this.#persistMutation(treeId, actorId, config, updated, 'CROSS_TREE_RELATIONSHIP_DELETED', [removed.source, removed.target]);
    return updated;
  }

  async publish(treeId: string, actorId: string, expectedRevision: number): Promise<CompositeTreeConfig> {
    await requireCompositeAdminPermission(treeId, actorId);
    const config = await this.#readConfigWithRevisionCheck(treeId, expectedRevision);
    const now = new Date().toISOString();
    const updated = { ...this.#bumpRevision(config, now), publishedAt: now };
    await this.#persistMutation(treeId, actorId, config, updated, 'CONFIG_PUBLISHED');
    return updated;
  }

  /**
   * Returns all configured sources annotated with whether the given actor can
   * read the source tree independently. Used by the resolver to determine which
   * source blobs to load (Requirement 7.1–7.2, Task 3.4).
   */
  async resolveAuthorizedSources(
    treeId: string,
    actorId: string
  ): Promise<Array<{ sourceTreeId: string; tree: FamilyTree | undefined; accessible: boolean }>> {
    assertIdentifier(treeId, 'treeId');
    assertIdentifier(actorId, 'actorId');

    const config = await this.getConfig(treeId);
    const allTrees = await getTrees();
    const treeIndex = new Map(allTrees.map((t) => [t.id, t]));

    return config.sources.map((source) => {
      const sourceTree = treeIndex.get(source.sourceTreeId);
      if (!sourceTree) {
        return { sourceTreeId: source.sourceTreeId, tree: undefined, accessible: false };
      }
      const effectiveKind: FamilyTreeKind = sourceTree.kind ?? 'STANDALONE';
      if (effectiveKind !== 'STANDALONE') {
        return { sourceTreeId: source.sourceTreeId, tree: sourceTree, accessible: false };
      }
      const accessible = canAccessTree(sourceTree, actorId, 'READ');
      return { sourceTreeId: source.sourceTreeId, tree: sourceTree, accessible };
    });
  }

  // ---------------------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------------------

  async #requireCompositeTree(treeId: string): Promise<FamilyTree> {
    const trees = await getTrees();
    const tree = trees.find((t) => t.id === treeId);
    if (!tree) {
      throw new CompositeConfigError('NOT_FOUND', 'Family tree not found');
    }
    const effectiveKind: FamilyTreeKind = tree.kind ?? 'STANDALONE';
    if (effectiveKind !== 'COMPOSITE') {
      throw new CompositeConfigError(
        'NOT_COMPOSITE_TREE',
        'This operation requires a composite tree'
      );
    }
    return tree;
  }

  async #readConfigWithRevisionCheck(
    treeId: string,
    expectedRevision: number
  ): Promise<CompositeTreeConfig> {
    await this.#requireCompositeTree(treeId);
    const config = await getCompositeConfig(treeId);
    if (!config) {
      throw new CompositeConfigError(
        'INVALID_COMPOSITE_CONFIG',
        `Composite config not found for tree "${treeId}"`
      );
    }
    if (config.revision !== expectedRevision) {
      throw new CompositeConfigError(
        'STALE_CONFIG_REVISION',
        `Config revision mismatch: expected ${expectedRevision}, found ${config.revision}`
      );
    }
    return config;
  }

  async #assertReferencesInScope(config: CompositeTreeConfig, references: SourceReference[]): Promise<void> {
    for (const reference of references) {
      const source = config.sources.find((item) => item.sourceTreeId === reference.treeId);
      if (!source) throw new CompositeConfigError('INVALID_COMPOSITE_CONFIG', 'Reference source is not configured');
      const [members, relationships] = await Promise.all([getMembers(reference.treeId), getRelationships(reference.treeId)]);
      const scoped = resolveSourceScope(members, relationships, [], [], source);
      if (!members.some((member) => member.id === reference.memberId)) throw new CompositeConfigError('NOT_FOUND', `Member "${reference.memberId}" not found`);
      if (!scoped.members.some((member) => member.id === reference.memberId)) throw new CompositeConfigError('REFERENCE_OUT_OF_SCOPE', `Member "${reference.memberId}" is outside the active source scope`);
    }
  }

  async #persistMutation(treeId: string, actorId: string, previous: CompositeTreeConfig, next: CompositeTreeConfig, action: CompositeAuditAction, sourceReferences: SourceReference[] = []): Promise<void> {
    const createdAt = new Date().toISOString();
    await putCompositeConfig(treeId, next);
    const entries = await getCompositeAuditLog(treeId);
    await putCompositeAuditLog(treeId, [...entries, {
      id: `audit_${Date.now()}_${Math.random().toString(36).slice(2)}`,
      compositeTreeId: treeId,
      actorId,
      action,
      revision: next.revision,
      previousData: summarizeConfig(previous),
      newData: { ...summarizeConfig(next), sourceReferences },
      sourceReference: sourceReferences[0],
      timestamp: createdAt
    }]);
  }

  #bumpRevision(config: CompositeTreeConfig, now: string): CompositeTreeConfig {
    return {
      ...config,
      revision: config.revision + 1,
      updatedAt: now
    };
  }
}

// ---------------------------------------------------------------------------
// Utility functions (pure, no I/O)
// ---------------------------------------------------------------------------

function summarizeConfig(config: CompositeTreeConfig): Record<string, unknown> {
  return { revision: config.revision, sourceIds: config.sources.map((source) => source.id), identityGroupIds: config.identityGroups.map((group) => group.id), relationshipIds: config.crossTreeRelationships.map((relationship) => relationship.id), publishedAt: config.publishedAt };
}

function assertIdentifier(value: string, field: string): void {
  if (!value?.trim()) {
    throw new CompositeConfigError('INVALID_INPUT', `${field} is required`);
  }
}

export const compositeConfigService = new CompositeConfigService();
export default compositeConfigService;
