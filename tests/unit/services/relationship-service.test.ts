import { describe, expect, it } from 'vitest';
import { getRelationships } from '@/lib/blob/readers';
import { putMembers, putRelationships } from '@/lib/blob/writers';
import { detectCycles } from '@/lib/algorithms/cycle-detection';
import { calculateGenerations } from '@/lib/algorithms/generation';
import { RelationshipService } from '@/lib/services/relationship-service';
import { buildMember, buildRelationship } from '../../utils/factories';

describe('RelationshipService', () => {
  it('creates and deletes both directed sides of a relationship', async () => {
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
    expect(stored).toHaveLength(2);
    expect(stored).toEqual(expect.arrayContaining([
      expect.objectContaining({ sourceMemberId: parent.id, targetMemberId: child.id }),
      expect.objectContaining({ sourceMemberId: child.id, targetMemberId: parent.id })
    ]));

    await service.deleteRelationship('tree_1', created.id);
    await expect(getRelationships('tree_1')).resolves.toEqual([]);
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
});
