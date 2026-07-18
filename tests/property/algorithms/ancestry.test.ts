import fc from 'fast-check';
import { describe, expect, it } from 'vitest';
import type { Member, Relationship } from '@/data/types';
import { getAncestryPath, getAncestrySubgraph } from '@/lib/algorithms/ancestry';

describe('Feature: family-genealogy-management, Property 9: Ancestry Subgraph Completeness', () => {
  it('always returns a root-to-target sequence of parent-child edges', () => {
    fc.assert(
      fc.property(
        fc.array(fc.nat(), { minLength: 1, maxLength: 80 }),
        fc.nat(),
        (parentChoices, requestedTarget) => {
          const memberCount = parentChoices.length + 1;
          const members = Array.from({ length: memberCount }, (_, index) => member(index));
          const canonical: Relationship[] = [];

          for (let child = 1; child < memberCount; child += 1) {
            const parent = parentChoices[child - 1] % child;
            canonical.push(relationship(parent, child, `edge-${child}`));
          }

          const targetId = `m${requestedTarget % memberCount}`;
          const path = getAncestryPath(members, canonical, targetId);

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

  it('includes every direct parent branch and spouse context', () => {
    fc.assert(
      fc.property(fc.integer({ min: 2, max: 20 }), (parentCount) => {
        const target = member(0);
        const parents = Array.from({ length: parentCount }, (_, index) => member(index + 1));
        const spouse = member(parentCount + 1);
        const members = [target, ...parents, spouse];
        const relationships = [
          ...parents.map((parent) => relationshipByIds(parent.id, target.id, `parent-${parent.id}`)),
          relationshipByIds(parents[0].id, spouse.id, 'spouse', 'SPOUSE')
        ];
        const subgraph = getAncestrySubgraph(members, relationships, target.id);

        expect(new Set(subgraph.memberIds)).toEqual(new Set(members.map((item) => item.id)));
        expect(subgraph.parentChildEdges).toHaveLength(parentCount);
        expect(subgraph.spouseEdges).toEqual([
          { sourceMemberId: parents[0].id, targetMemberId: spouse.id }
        ]);
      }),
      { numRuns: 50 }
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
  return relationshipByIds(`m${source}`, `m${target}`, id);
}

function relationshipByIds(
  sourceMemberId: string,
  targetMemberId: string,
  id: string,
  type: Relationship['type'] = 'PARENT_CHILD'
): Relationship {
  return {
    id,
    treeId: 'tree-property',
    sourceMemberId,
    targetMemberId,
    type,
    createdAt: '2026-01-01T00:00:00.000Z'
  };
}
