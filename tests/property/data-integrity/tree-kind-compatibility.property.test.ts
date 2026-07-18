import fc from 'fast-check';
import { describe, expect, it } from 'vitest';
import { familyTreeSchema } from '@/data/schemas';
import type { FamilyTree } from '@/data/types';
import { BLOB_PATHS } from '@/lib/blob/client';
import { getTrees } from '@/lib/blob/readers';
import { mockBlobStorage } from '../../utils/mock-blob-storage';

const isoTimestampArbitrary = fc.integer({
  min: Date.parse('2000-01-01T00:00:00.000Z'),
  max: Date.parse('2099-12-31T23:59:59.999Z')
}).map((timestamp) => new Date(timestamp).toISOString());

const identifierArbitrary = fc.stringMatching(/^[A-Za-z0-9][A-Za-z0-9_-]{0,31}$/);
const nonBlankTextArbitrary = fc.stringMatching(/^[A-Za-z0-9][A-Za-z0-9 _-]{0,63}$/);

const legacyTreeArbitrary = fc.record({
  id: identifierArbitrary,
  name: nonBlankTextArbitrary,
  description: fc.option(nonBlankTextArbitrary, { nil: undefined }),
  ownerId: identifierArbitrary,
  memberships: fc.array(
    fc.record({
      userId: identifierArbitrary,
      role: fc.constantFrom('ADMIN' as const, 'EDITOR' as const, 'VIEWER' as const),
      createdAt: isoTimestampArbitrary
    }),
    { maxLength: 8 }
  ),
  createdAt: isoTimestampArbitrary,
  updatedAt: isoTimestampArbitrary
});

describe('FamilyTree kind backward compatibility', () => {
  it('normalizes every valid legacy record without changing its existing fields', () => {
    fc.assert(fc.property(legacyTreeArbitrary, (legacyTree) => {
      const normalized = familyTreeSchema.parse(legacyTree);

      expect(normalized).toEqual({
        ...legacyTree,
        kind: 'STANDALONE'
      });
      expect(Object.fromEntries(
        Object.entries(normalized).filter(([key]) => key !== 'kind')
      )).toEqual(legacyTree);
    }));
  });

  it('normalizes legacy blobs at read time without eagerly rewriting storage', async () => {
    await fc.assert(fc.asyncProperty(
      fc.array(legacyTreeArbitrary, { minLength: 1, maxLength: 20 }),
      async (legacyTrees) => {
        const serialized = JSON.stringify(legacyTrees);
        mockBlobStorage.put(BLOB_PATHS.trees(), serialized);

        const trees = await getTrees();

        expect(trees).toEqual(legacyTrees.map((tree): FamilyTree => ({
          ...tree,
          kind: 'STANDALONE'
        })));
        expect(mockBlobStorage.get(BLOB_PATHS.trees())?.body).toBe(serialized);
      }
    ), { numRuns: 50 });
  });
});