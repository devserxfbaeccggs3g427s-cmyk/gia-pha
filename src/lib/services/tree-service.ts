import { nanoid } from 'nanoid';
import { createCompositeTreeInputSchema, createTreeSchema, updateTreeSchema } from '@/data/schemas';
import { createInitialTree } from '@/data/seed';
import type { CompositeTreeConfig, FamilyTree, FamilyTreeKind, Member, Relationship, ShareLink } from '@/data/types';
import {
  getAncestryPath as findAncestryPath,
  getAncestrySubgraph as findAncestrySubgraph,
  type AncestrySubgraph
} from '@/lib/algorithms/ancestry';
import { calculateGenerations as calculateGenerationMap, type GenerationMap } from '@/lib/algorithms/generation';
import { BLOB_PATHS, deleteBlob, deleteBlobs, listBlobs, readBlob } from '@/lib/blob/client';
import { getMediaMetadata, getMembers, getRelationships, getTrees } from '@/lib/blob/readers';
import { putCompositeConfig, putTrees } from '@/lib/blob/writers';

export interface FamilyTreeFull extends FamilyTree {
  members: Member[];
  relationships: Relationship[];
}

export class TreeServiceError extends Error {
  constructor(
    public readonly code: 'NOT_FOUND' | 'INVALID_INPUT',
    message: string
  ) {
    super(message);
    this.name = 'TreeServiceError';
  }
}

export class TreeService {
  async createTree(userId: string, data: unknown): Promise<FamilyTree> {
    assertIdentifier(userId, 'userId');
    const raw = data as Record<string, unknown> | null | undefined;
    if (raw && typeof raw === 'object' && raw['kind'] === 'COMPOSITE') {
      return this.createCompositeTree(userId, data);
    }
    const input = createTreeSchema.parse(data);
    const trees = await getTrees();
    const now = new Date().toISOString();
    const tree = createInitialTree({
      id: nanoid(),
      ownerId: userId,
      name: input.name,
      ...(input.description !== undefined ? { description: input.description } : {}),
      now
    });

    await putTrees([...trees, tree]);
    return tree;
  }

  async createCompositeTree(userId: string, data: unknown): Promise<FamilyTree> {
    assertIdentifier(userId, 'userId');
    if (process.env.COMPOSITE_TREES_ENABLED === 'false') {
      throw new TreeServiceError('INVALID_INPUT', 'Composite family trees are not enabled');
    }
    const input = createCompositeTreeInputSchema.parse(data);
    const trees = await getTrees();
    const now = new Date().toISOString();
    const treeId = nanoid();

    const tree: FamilyTree = {
      id: treeId,
      kind: 'COMPOSITE' as FamilyTreeKind,
      name: input.name,
      ...(input.description !== undefined ? { description: input.description } : {}),
      ownerId: userId,
      memberships: [{ userId, role: 'ADMIN', createdAt: now }],
      createdAt: now,
      updatedAt: now
    };

    const emptyConfig: CompositeTreeConfig = {
      treeId,
      schemaVersion: 1,
      revision: 0,
      sources: [],
      identityGroups: [],
      crossTreeRelationships: [],
      createdAt: now,
      updatedAt: now
    };

    await putCompositeConfig(treeId, emptyConfig);
    try {
      await putTrees([...trees, tree]);
    } catch (error) {
      try {
        await deleteBlob(BLOB_PATHS.compositeConfig(treeId));
      } catch (rollbackError) {
        throw new AggregateError([error, rollbackError], 'Composite tree creation and config rollback failed');
      }
      throw error;
    }

    return tree;
  }

  async listTreesForUser(userId: string): Promise<FamilyTree[]> {
    assertIdentifier(userId, 'userId');
    const trees = await getTrees();
    return trees.filter(
      (tree) => tree.ownerId === userId || tree.memberships.some((membership) => membership.userId === userId)
    );
  }

  async getTree(treeId: string): Promise<FamilyTree> {
    assertIdentifier(treeId, 'treeId');
    const tree = (await getTrees()).find((candidate) => candidate.id === treeId);
    if (!tree) throw new TreeServiceError('NOT_FOUND', 'Family tree not found');
    return tree;
  }

  async updateTree(treeId: string, data: unknown): Promise<FamilyTree> {
    assertIdentifier(treeId, 'treeId');
    const input = updateTreeSchema.parse(data);
    const trees = await getTrees();
    const index = trees.findIndex((candidate) => candidate.id === treeId);
    if (index < 0) throw new TreeServiceError('NOT_FOUND', 'Family tree not found');

    const current = trees[index];
    const updated: FamilyTree = {
      ...current,
      ...input,
      id: current.id,
      ownerId: current.ownerId,
      memberships: current.memberships,
      createdAt: current.createdAt,
      updatedAt: new Date().toISOString()
    };
    trees[index] = updated;
    await putTrees(trees);
    return updated;
  }

  async deleteTree(treeId: string): Promise<void> {
    assertIdentifier(treeId, 'treeId');
    const trees = await getTrees();
    const tree = trees.find((candidate) => candidate.id === treeId);
    if (!tree) {
      throw new TreeServiceError('NOT_FOUND', 'Family tree not found');
    }
    const effectiveKind = tree.kind ?? 'STANDALONE';

    // Remove the discoverable tree first. If cleanup is interrupted, only
    // unreachable orphan blobs remain; a tree never points to partial data.
    await putTrees(trees.filter((candidate) => candidate.id !== treeId));

    if (effectiveKind === 'COMPOSITE') {
      const [manifests, mutations, shareLinks] = await Promise.all([
        listBlobs(BLOB_PATHS.compositeManifestPrefix(treeId)),
        listBlobs(BLOB_PATHS.compositeMutationPrefix(treeId)),
        readBlob<ShareLink[]>(BLOB_PATHS.shareLinks(treeId))
      ]);
      await deleteBlobs([
        BLOB_PATHS.compositeConfig(treeId),
        BLOB_PATHS.compositePublishedConfig(treeId),
        BLOB_PATHS.compositeChangeLogs(treeId),
        BLOB_PATHS.shareLinks(treeId),
        ...(shareLinks ?? []).map((link) => BLOB_PATHS.shareLink(link.token)),
         ...manifests.map((manifest) => manifest.pathname),
         ...mutations.map((mutation) => mutation.pathname)
      ]);
    } else {
      const media = await getMediaMetadata(treeId);
      await deleteBlobs([
        BLOB_PATHS.members(treeId),
        BLOB_PATHS.relationships(treeId),
        BLOB_PATHS.events(treeId),
        BLOB_PATHS.mediaMetadata(treeId),
        BLOB_PATHS.albums(treeId),
        BLOB_PATHS.changeLogs(treeId),
        ...media.flatMap((item) => [item.blobUrl, ...(item.thumbnailUrl ? [item.thumbnailUrl] : [])])
      ]);
    }
  }

  async getTreeWithMembers(treeId: string): Promise<FamilyTreeFull> {
    const [tree, members, relationships] = await Promise.all([
      this.getTree(treeId),
      getMembers(treeId),
      getRelationships(treeId)
    ]);
    return { ...tree, members, relationships };
  }

  async calculateGenerations(treeId: string): Promise<GenerationMap> {
    await this.getTree(treeId);
    const [members, relationships] = await Promise.all([
      getMembers(treeId),
      getRelationships(treeId)
    ]);
    return calculateGenerationMap(members, relationships);
  }

  async getAncestryPath(memberId: string, treeId: string): Promise<Member[]> {
    assertIdentifier(memberId, 'memberId');
    await this.getTree(treeId);
    const [members, relationships] = await Promise.all([
      getMembers(treeId),
      getRelationships(treeId)
    ]);
    if (!members.some((member) => member.id === memberId)) {
      throw new TreeServiceError('NOT_FOUND', 'Member not found');
    }
    return findAncestryPath(members, relationships, memberId);
  }

  async getAncestrySubgraph(
    memberId: string,
    treeId: string,
    options: { includeSpouses?: boolean } = {}
  ): Promise<AncestrySubgraph> {
    assertIdentifier(memberId, 'memberId');
    await this.getTree(treeId);
    const [members, relationships] = await Promise.all([
      getMembers(treeId),
      getRelationships(treeId)
    ]);
    if (!members.some((member) => member.id === memberId)) {
      throw new TreeServiceError('NOT_FOUND', 'Member not found');
    }
    return findAncestrySubgraph(members, relationships, memberId, options);
  }
}

function assertIdentifier(value: string, field: string): void {
  if (!value?.trim()) throw new TreeServiceError('INVALID_INPUT', `${field} is required`);
}

export const treeService = new TreeService();
export default treeService;

export const createTree = treeService.createTree.bind(treeService);
export const createCompositeTree = treeService.createCompositeTree.bind(treeService);
export const listTreesForUser = treeService.listTreesForUser.bind(treeService);
export const getTree = treeService.getTree.bind(treeService);
export const updateTree = treeService.updateTree.bind(treeService);
export const deleteTree = treeService.deleteTree.bind(treeService);
export const getTreeWithMembers = treeService.getTreeWithMembers.bind(treeService);
export const calculateGenerations = treeService.calculateGenerations.bind(treeService);
export const getAncestryPath = treeService.getAncestryPath.bind(treeService);
export const getAncestrySubgraph = treeService.getAncestrySubgraph.bind(treeService);
