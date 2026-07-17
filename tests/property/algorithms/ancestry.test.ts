import fc from 'fast-check';
import { describe, expect, it } from 'vitest';
import type { Member, Relationship } from '@/data/types';
import { getAncestryPath } from '@/lib/algorithms/ancestry';

describe('Feature: family-genealogy-management, Property 9: Ancestry Path Validity', () => {
  it('always returns a root-to-target sequence of parent-child edges', () => {
    fc.assert(
      fc.property(
        fc.array(fc.nat(), { minLength: 1, maxLength: 80 }),
        fc.nat(),
        fc.boolean(),
        (parentChoices, requestedTarget, persistInverse) => {
          const memberCount = parentChoices.length + 1;
          const members = Array.from({ length: memberCount }, (_, index) => member(index));
          const canonical: Relationship[] = [];

          for (let child = 1; child < memberCount; child += 1) {
            const parent = parentChoices[child - 1] % child;
            canonical.push(relationship(parent, child, `edge-${child}`));
          }

          const relationships = persistInverse
            ? canonical.flatMap((edge) => [
                edge,
                relationship(
                  Number(edge.targetMemberId.slice(1)),
                  Number(edge.sourceMemberId.slice(1)),
                  `${edge.id}-inverse`
                )
              ])
            : canonical;
          const targetId = `m${requestedTarget % memberCount}`;
          const path = getAncestryPath(members, relationships, targetId);

          expect(path.length).toBeGreaterThan(0);
          expect(path.at(-1)?.id).toBe(targetId);

          const childIds = new Set(canonical.map((edge) => edge.targetMemberId));
          expect(childIds.has(path[0].id)).toBe(false);
          for (let index = 1; index < path.length; index += 1) {
            expect(canonical.some(
              (edge) => edge.sourceMemberId === path[index - 1].id && edge.targetMemberId === path[index].id
            )).toBe(true);
          }
        }
      ),
      { numRuns: 100 }
    );
  });
});

function member(index: number): Member {
  return {
    id: `m${index}`,
    treeId: 'tree-property',
    firstName: `Member ${index}`,
    lastName: 'Property',
    fullName: `Property Member ${index}`,
    gender: 'OTHER',
    isAlive: true,
    createdAt: '2026-01-01T00:00:00.000Z',
    updatedAt: '2026-01-01T00:00:00.000Z'
  };
}

function relationship(source: number, target: number, id: string): Relationship {
  return {
    id,
    treeId: 'tree-property',
    sourceMemberId: `m${source}`,
    targetMemberId: `m${target}`,
    type: 'PARENT_CHILD',
    createdAt: '2026-01-01T00:00:00.000Z'
  };
}
