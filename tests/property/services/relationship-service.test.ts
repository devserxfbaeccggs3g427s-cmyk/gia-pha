import fc from 'fast-check';
import { describe, expect, it } from 'vitest';
import { getRelationships } from '@/lib/blob/readers';
import { putMembers } from '@/lib/blob/writers';
import { RelationshipService } from '@/lib/services/relationship-service';
import { buildMember } from '../../utils/factories';

describe('Feature: family-genealogy-management, Property 6: Derived Inverse Relationship Symmetry', () => {
  it('persists one logical row while exposing parent and child perspectives', async () => {
    let sequence = 0;
    await fc.assert(
      fc.asyncProperty(fc.nat(), async (rawId) => {
        const suffix = `${rawId}-${sequence++}`;
        const treeId = `tree-property-${suffix}`;
        const parentId = `parent-${suffix}`;
        const childId = `child-${suffix}`;
        await putMembers(treeId, [
          buildMember({ id: parentId, treeId }),
          buildMember({ id: childId, treeId })
        ]);
        const service = new RelationshipService();
        const created = await service.createRelationship(treeId, {
          sourceMemberId: parentId,
          targetMemberId: childId,
          type: 'PARENT_CHILD'
        });

        await expect(getRelationships(treeId)).resolves.toEqual([created]);
        await expect(service.getRelationshipsForMember(treeId, parentId)).resolves.toEqual([
          expect.objectContaining({ id: created.id, memberId: parentId, relatedMemberId: childId, role: 'PARENT' })
        ]);
        await expect(service.getRelationshipsForMember(treeId, childId)).resolves.toEqual([
          expect.objectContaining({ id: created.id, memberId: childId, relatedMemberId: parentId, role: 'CHILD' })
        ]);
      }),
      { numRuns: 30 }
    );
  });
});
