import fc from 'fast-check';
import { describe, expect, it } from 'vitest';
import type { Relationship } from '@/data/types';
import { detectCycles } from '@/lib/algorithms/cycle-detection';

describe('Feature: family-genealogy-management, Property 7: Cycle Detection Correctness', () => {
  it('rejects exactly the edges that point from a descendant back to its ancestor', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 2, max: 80 }),
        fc.nat(),
        fc.nat(),
        (memberCount, rawSource, rawTarget) => {
          const source = rawSource % memberCount;
          const target = rawTarget % memberCount;
          expect(detectCycles(chain(memberCount), memberId(source), memberId(target)))
            .toBe(target <= source);
        }
      ),
      { numRuns: 200 }
    );
  });

  it('accepts any number of parents converging on one child', () => {
    fc.assert(
      fc.property(fc.integer({ min: 2, max: 40 }), (parentCount) => {
        const existing = Array.from({ length: parentCount - 1 }, (_, index) =>
          relationship(memberId(index), 'child', `parent-${index}`)
        );
        expect(detectCycles(existing, memberId(parentCount - 1), 'child')).toBe(false);
      }),
      { numRuns: 100 }
    );
  });
});

function chain(memberCount: number): Relationship[] {
  return Array.from({ length: memberCount - 1 }, (_, index) =>
    relationship(memberId(index), memberId(index + 1), `edge-${index}`)
  );
}

function memberId(index: number): string {
  return `m${index}`;
}

function relationship(sourceMemberId: string, targetMemberId: string, id: string): Relationship {
  return {
    id,
    treeId: 'tree-property',
    sourceMemberId,
    targetMemberId,
    type: 'PARENT_CHILD',
    createdAt: '2026-01-01T00:00:00.000Z'
  };
}
