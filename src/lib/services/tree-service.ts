import { nanoid } from 'nanoid';
import { createTreeSchema, updateTreeSchema } from '@/data/schemas';
import { createInitialTree } from '@/data/seed';
import type { FamilyTree, Member, Relationship } from '@/data/types';
import { getAncestryPath as findAncestryPath } from '@/lib/algorithms/ancestry';
import { calculateGenerations as calculateGenerationMap, type GenerationMap } from '@/lib/algorithms/generation';
import { BLOB_PATHS, deleteBlobs } from '@/lib/blob/client';
import { getMembers, getRelationships, getTrees } from '@/lib/blob/readers';
import { putTrees } from '@/lib/blob/writers';

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
    if (!trees.some((candidate) => candidate.id === treeId)) {
      throw new TreeServiceError('NOT_FOUND', 'Family tree not found');
    }

    // Remove the discoverable tree first. If cleanup is interrupted, only
    // unreachable orphan blobs remain; a tree never points to partial data.
    await putTrees(trees.filter((candidate) => candidate.id !== treeId));
    await deleteBlobs([
      BLOB_PATHS.members(treeId),
      BLOB_PATHS.relationships(treeId),
      BLOB_PATHS.events(treeId),
      BLOB_PATHS.mediaMetadata(treeId),
      BLOB_PATHS.changeLogs(treeId)
    ]);
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
}

function assertIdentifier(value: string, field: string): void {
  if (!value?.trim()) throw new TreeServiceError('INVALID_INPUT', `${field} is required`);
}

export const treeService = new TreeService();
export default treeService;

export const createTree = treeService.createTree.bind(treeService);
export const listTreesForUser = treeService.listTreesForUser.bind(treeService);
export const getTree = treeService.getTree.bind(treeService);
export const updateTree = treeService.updateTree.bind(treeService);
export const deleteTree = treeService.deleteTree.bind(treeService);
export const getTreeWithMembers = treeService.getTreeWithMembers.bind(treeService);
export const calculateGenerations = treeService.calculateGenerations.bind(treeService);
export const getAncestryPath = treeService.getAncestryPath.bind(treeService);
