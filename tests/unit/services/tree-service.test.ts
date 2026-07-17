import { describe, expect, it } from 'vitest';
import { BLOB_PATHS } from '@/lib/blob/client';
import { getMembers, getRelationships, getTrees } from '@/lib/blob/readers';
import { putMembers, putRelationships, putTrees } from '@/lib/blob/writers';
import { TreeService, TreeServiceError } from '@/lib/services/tree-service';
import { mockBlobStorage } from '../../utils/mock-blob-storage';
import { buildFamilyTree, buildMember, buildRelationship } from '../../utils/factories';

describe('TreeService', () => {
  it('creates an owned tree and only lists trees visible to the user', async () => {
    const hidden = buildFamilyTree({ id: 'hidden', ownerId: 'someone-else' });
    const shared = buildFamilyTree({
      id: 'shared',
      ownerId: 'someone-else',
      memberships: [{ userId: 'user-1', role: 'VIEWER', createdAt: '2026-01-01T00:00:00.000Z' }]
    });
    await putTrees([hidden, shared]);
    const service = new TreeService();

    const created = await service.createTree('user-1', {
      name: 'Gia phả Nguyễn',
      description: 'Nhánh chính'
    });

    expect(created).toMatchObject({
      name: 'Gia phả Nguyễn',
      ownerId: 'user-1',
      memberships: [expect.objectContaining({ userId: 'user-1', role: 'ADMIN' })]
    });
    await expect(service.listTreesForUser('user-1')).resolves.toEqual([shared, created]);
  });

  it('updates mutable metadata while preserving ownership and memberships', async () => {
    const tree = buildFamilyTree({ id: 'tree-1', ownerId: 'owner-1', name: 'Old name' });
    await putTrees([tree]);
    const service = new TreeService();

    const updated = await service.updateTree(tree.id, { name: 'New name' });

    expect(updated.name).toBe('New name');
    expect(updated.ownerId).toBe(tree.ownerId);
    expect(updated.memberships).toEqual(tree.memberships);
    expect(updated.createdAt).toBe(tree.createdAt);
  });

  it('loads full tree data, calculates generations and resolves ancestry', async () => {
    const tree = buildFamilyTree({ id: 'tree-1' });
    const root = buildMember({ id: 'root', treeId: tree.id });
    const child = buildMember({ id: 'child', treeId: tree.id });
    const forward = buildRelationship({
      id: 'forward', treeId: tree.id, sourceMemberId: root.id, targetMemberId: child.id
    });
    const inverse = buildRelationship({
      id: 'inverse', treeId: tree.id, sourceMemberId: child.id, targetMemberId: root.id
    });
    await putTrees([tree]);
    await putMembers(tree.id, [root, child]);
    await putRelationships(tree.id, [forward, inverse]);
    const service = new TreeService();

    await expect(service.getTreeWithMembers(tree.id)).resolves.toMatchObject({
      id: tree.id,
      members: [root, child],
      relationships: [forward, inverse]
    });
    await expect(service.calculateGenerations(tree.id)).resolves.toEqual(new Map([
      [root.id, 0],
      [child.id, 1]
    ]));
    await expect(service.getAncestryPath(child.id, tree.id)).resolves.toEqual([root, child]);
  });

  it('deletes tree metadata and all per-tree JSON blobs', async () => {
    const tree = buildFamilyTree({ id: 'tree-delete' });
    await putTrees([tree]);
    await putMembers(tree.id, [buildMember({ treeId: tree.id })]);
    await putRelationships(tree.id, []);
    const service = new TreeService();

    await service.deleteTree(tree.id);

    await expect(getTrees()).resolves.toEqual([]);
    await expect(getMembers(tree.id)).resolves.toEqual([]);
    await expect(getRelationships(tree.id)).resolves.toEqual([]);
    expect(mockBlobStorage.get(BLOB_PATHS.members(tree.id))).toBeUndefined();
    expect(mockBlobStorage.get(BLOB_PATHS.relationships(tree.id))).toBeUndefined();
  });

  it('reports missing trees and members explicitly', async () => {
    const service = new TreeService();
    await expect(service.getTree('missing')).rejects.toEqual(
      expect.objectContaining({ code: 'NOT_FOUND' } satisfies Partial<TreeServiceError>)
    );
  });
});
