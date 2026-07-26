import fc from 'fast-check';
import { describe, expect, it } from 'vitest';
import type { Member, Relationship } from '@/data/types';
import { calculateGenerations } from '@/lib/algorithms/generation';

describe('Feature: family-genealogy-management, Property 8: Generation Calculation Invariants', () => {
  it('assigns every spouse couple to the same generation throughout a lineage', () => {
    fc.assert(
      fc.property(fc.integer({ min: 2, max: 30 }), fc.boolean(), (generationCount, reverseMembers) => {
        const { members, relationships } = coupledLineage(generationCount);
        const generations = calculateGenerations(reverseMembers ? [...members].reverse() : members, relationships);

        for (let generation = 0; generation < generationCount; generation += 1) {
          expect(generations.get(personId(generation))).toBe(generation);
          expect(generations.get(spouseId(generation))).toBe(generation);
        }
      }),
      { numRuns: 100 }
    );
  });

  it('keeps both parents one generation above their child component', () => {
    fc.assert(
      fc.property(fc.integer({ min: 1, max: 25 }), (childGeneration) => {
        const { members, relationships } = coupledLineage(childGeneration + 1, true);
        const generations = calculateGenerations(members, relationships);
        for (const relationship of relationships.filter(item => item.type === 'PARENT_CHILD')) {
          expect(generations.get(relationship.targetMemberId))
            .toBe((generations.get(relationship.sourceMemberId) ?? -1) + 1);
        }
        for (const relationship of relationships.filter(item => item.type === 'SPOUSE')) {
          expect(generations.get(relationship.sourceMemberId))
            .toBe(generations.get(relationship.targetMemberId));
        }
      }),
      { numRuns: 100 }
    );
  });
});

function coupledLineage(generationCount: number, connectBothParents = false): { members: Member[]; relationships: Relationship[] } {
  const members: Member[] = [];
  const relationships: Relationship[] = [];
  for (let generation = 0; generation < generationCount; generation += 1) {
    members.push(member(personId(generation)), member(spouseId(generation)));
    relationships.push(relationship(personId(generation), spouseId(generation), 'SPOUSE', `spouse-${generation}`));
    if (generation === 0) continue;
    relationships.push(relationship(personId(generation - 1), personId(generation), 'PARENT_CHILD', `parent-${generation}`));
    if (connectBothParents) relationships.push(relationship(spouseId(generation - 1), personId(generation), 'PARENT_CHILD', `second-parent-${generation}`));
  }
  return { members, relationships };
}

function member(id: string): Member {
  return { id, treeId: 'tree-property', firstName: id, lastName: 'Property', fullName: `Property ${id}`, gender: 'OTHER', isAlive: true, createdAt: '2026-01-01T00:00:00.000Z', updatedAt: '2026-01-01T00:00:00.000Z' };
}

function relationship(sourceMemberId: string, targetMemberId: string, type: Relationship['type'], id: string): Relationship {
  return { id, treeId: 'tree-property', sourceMemberId, targetMemberId, type, createdAt: '2026-01-01T00:00:00.000Z' };
}

function personId(generation: number): string { return `generation-${generation}-person`; }
function spouseId(generation: number): string { return `generation-${generation}-spouse`; }
