import { describe, expect, it } from 'vitest';
import * as fc from 'fast-check';
import type { Member } from '@/data/types';
import {
  SEARCHABLE_MEMBER_FIELDS,
  SearchService,
  type SearchableMemberField
} from '@/lib/services/search-service';
import { normalizeVietnamese } from '@/lib/utils/vietnamese';

const vietnameseWords = [
  'Nguyễn', 'Trần', 'Đặng', 'Thảo', 'Hồng', 'Huế', 'Đà Nẵng', 'kỹ sư', 'bác sĩ', 'nông dân'
] as const;

const optionalWord = fc.option(fc.constantFrom(...vietnameseWords), { nil: undefined });

const searchableMemberArbitrary = fc.record({
  fullName: fc.constantFrom(...vietnameseWords),
  nickname: optionalWord,
  occupation: optionalWord,
  placeOfBirth: optionalWord
});

describe('Feature: family-genealogy-management, Property 10: Search Correctness with Vietnamese Normalization', () => {
  it('returns exactly the members whose searchable field contains the normalized query', async () => {
    await fc.assert(
      fc.asyncProperty(
        fc.array(searchableMemberArbitrary, { minLength: 1, maxLength: 40 }),
        fc.constantFrom(...vietnameseWords),
        async (records, query) => {
          const members = records.map((record, index) => memberFromRecord(index, record));
          const service = new SearchService(async () => members);
          const results = await service.search('tree-property', normalizeVietnamese(query));
          const normalizedQuery = normalizeVietnamese(query);
          const expectedIds = members
            .filter((member) => searchableValues(member).some((value) => value.includes(normalizedQuery)))
            .map((member) => member.id)
            .sort();
          const actualIds = results.map((result) => result.member.id).sort();

          expect(actualIds).toEqual(expectedIds);
          for (const result of results) {
            expect(result.matchedFields.length).toBeGreaterThan(0);
            expect(result.matchedFields.some((field) =>
              normalizeVietnamese(result.member[field] ?? '').includes(normalizedQuery)
            )).toBe(true);
          }
        }
      ),
      { numRuns: 100 }
    );
  });
});

function memberFromRecord(
  index: number,
  record: {
    fullName: string;
    nickname?: string;
    occupation?: string;
    placeOfBirth?: string;
  }
): Member {
  return {
    id: `member-${index}`,
    treeId: 'tree-property',
    firstName: record.fullName,
    lastName: 'Test',
    fullName: record.fullName,
    ...(record.nickname ? { nickname: record.nickname } : {}),
    ...(record.occupation ? { occupation: record.occupation } : {}),
    ...(record.placeOfBirth ? { placeOfBirth: record.placeOfBirth } : {}),
    gender: 'OTHER',
    isAlive: true,
    createdAt: '2026-01-01T00:00:00.000Z',
    updatedAt: '2026-01-01T00:00:00.000Z'
  };
}

function searchableValues(member: Member): string[] {
  return SEARCHABLE_MEMBER_FIELDS
    .map((field: SearchableMemberField) => member[field])
    .filter((value): value is string => Boolean(value))
    .map(normalizeVietnamese);
}
