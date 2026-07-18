import { describe, expect, it } from 'vitest';
import { getRelationships } from '@/lib/blob/readers';
import { putMembers, putRelationships } from '@/lib/blob/writers';
import { BLOB_PATHS, writeBlob } from '@/lib/blob/client';
import { detectCycles } from '@/lib/algorithms/cycle-detection';
import { calculateGenerations } from '@/lib/algorithms/generation';
import { RelationshipService } from '@/lib/services/relationship-service';
import { buildMember, buildRelationship } from '../../utils/factories';

describe('RelationshipService', () => {
  it('persists and deletes one canonical relationship record', async () => {
    const parent = buildMember({ id: 'parent', treeId: 'tree_1' });
    const child = buildMember({ id: 'child', treeId: 'tree_1' });
    await putMembers('tree_1', [parent, child]);
    const service = new RelationshipService();

    const created = await service.createRelationship('tree_1', {
      sourceMemberId: parent.id,
      targetMemberId: child.id,
      type: 'PARENT_CHILD'
    });
    const stored = await getRelationships('tree_1');
    expect(stored).toHaveLength(1);
    expect(stored).toEqual([
      expect.objectContaining({ sourceMemberId: parent.id, targetMemberId: child.id })
    ]);

    await service.deleteRelationship('tree_1', created.id);
    await expect(getRelationships('tree_1')).resolves.toEqual([]);
  });

  it('accepts multiple parents converging on the same child', async () => {
    const father = buildMember({ id: 'father', treeId: 'tree_1' });
    const mother = buildMember({ id: 'mother', treeId: 'tree_1' });
    const child = buildMember({ id: 'child', treeId: 'tree_1' });
    await putMembers('tree_1', [father, mother, child]);
    await putRelationships('tree_1', [buildRelationship({
      sourceMemberId: father.id,
      targetMemberId: child.id
    })]);
    const service = new RelationshipService();

    await expect(service.validateRelationship('tree_1', {
      sourceMemberId: mother.id,
      targetMemberId: child.id,
      type: 'PARENT_CHILD'
    })).resolves.toEqual({ valid: true, errors: [] });
  });

  it('lazily migrates legacy reciprocal parent-child rows on read', async () => {
    const parent = buildMember({ id: 'parent', treeId: 'tree_1' });
    const child = buildMember({ id: 'child', treeId: 'tree_1' });
    const forward = buildRelationship({ id: 'forward', sourceMemberId: parent.id, targetMemberId: child.id });
    const inverse = buildRelationship({ id: 'inverse', sourceMemberId: child.id, targetMemberId: parent.id });
    await writeBlob(BLOB_PATHS.relationships('tree_1'), [forward, inverse]);

    await expect(getRelationships('tree_1')).resolves.toEqual([forward]);
    await expect(getRelationships('tree_1')).resolves.toHaveLength(1);
  });

  it('returns a child perspective without persisting an inverse row', async () => {
    const parent = buildMember({ id: 'parent', treeId: 'tree_1' });
    const child = buildMember({ id: 'child', treeId: 'tree_1' });
    await putMembers('tree_1', [parent, child]);
    const service = new RelationshipService();
    await service.createRelationship('tree_1', {
      sourceMemberId: parent.id,
      targetMemberId: child.id,
      type: 'PARENT_CHILD'
    });

    await expect(service.getRelationshipsForMember('tree_1', child.id)).resolves.toEqual([
      expect.objectContaining({
        sourceMemberId: parent.id,
        targetMemberId: child.id,
        memberId: child.id,
        relatedMemberId: parent.id,
        role: 'CHILD'
      })
    ]);
  });

  it('rejects self references and parent-child cycles with specific validation errors', async () => {
    const a = buildMember({ id: 'a', treeId: 'tree_1' });
    const b = buildMember({ id: 'b', treeId: 'tree_1' });
    await putMembers('tree_1', [a, b]);
    await putRelationships('tree_1', [buildRelationship({ sourceMemberId: a.id, targetMemberId: b.id })]);
    const service = new RelationshipService();

    await expect(service.validateRelationship('tree_1', {
      sourceMemberId: b.id, targetMemberId: a.id, type: 'PARENT_CHILD'
    })).resolves.toMatchObject({ valid: false, errors: expect.arrayContaining([
      'The relationship would create a parent-child cycle'
    ]) });
    await expect(service.validateRelationship('tree_1', {
      sourceMemberId: a.id, targetMemberId: a.id, type: 'PARENT_CHILD'
    })).resolves.toMatchObject({ valid: false });
    expect(detectCycles([buildRelationship({ sourceMemberId: a.id, targetMemberId: b.id })], b.id, a.id)).toBe(true);
  });

  it('assigns roots, children and spouses to the expected generations', () => {
    const root = buildMember({ id: 'root' });
    const spouse = buildMember({ id: 'spouse' });
    const child = buildMember({ id: 'child' });
    const generations = calculateGenerations([root, spouse, child], [
      buildRelationship({ sourceMemberId: root.id, targetMemberId: child.id, type: 'PARENT_CHILD' }),
      buildRelationship({ sourceMemberId: root.id, targetMemberId: spouse.id, type: 'SPOUSE' })
    ]);
    expect(generations.get(root.id)).toBe(0);
    expect(generations.get(spouse.id)).toBe(0);
    expect(generations.get(child.id)).toBe(1);
  });

  it('keeps a spouse without recorded parents in the partner child generation', () => {
    const father = buildMember({ id: 'father' });
    const mother = buildMember({ id: 'mother' });
    const child = buildMember({ id: 'child' });
    const spouse = buildMember({ id: 'spouse', gender: 'FEMALE' });
    const relationships = [
      buildRelationship({ sourceMemberId: father.id, targetMemberId: mother.id, type: 'SPOUSE' }),
      buildRelationship({ sourceMemberId: child.id, targetMemberId: spouse.id, type: 'SPOUSE' }),
      buildRelationship({ sourceMemberId: father.id, targetMemberId: child.id, type: 'PARENT_CHILD' }),
      buildRelationship({ sourceMemberId: mother.id, targetMemberId: child.id, type: 'PARENT_CHILD' })
    ];

    const generations = calculateGenerations([father, mother, child, spouse], relationships);
    expect(generations.get(father.id)).toBe(0);
    expect(generations.get(mother.id)).toBe(0);
    expect(generations.get(child.id)).toBe(1);
    expect(generations.get(spouse.id)).toBe(1);
  });
});
