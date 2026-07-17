import { describe, expect, it } from 'vitest';
import * as fc from 'fast-check';
import type { Gender, Member } from '@/data/types';
import { SearchService, type MemberFilters } from '@/lib/services/search-service';
import { normalizeVietnamese } from '@/lib/utils/vietnamese';

const locations = ['Hà Nội', 'Đà Nẵng', 'Huế'] as const;

const memberArbitrary = fc.record({
  gender: fc.constantFrom<Gender>('MALE', 'FEMALE', 'OTHER'),
  generation: fc.integer({ min: 0, max: 8 }),
  birthYear: fc.integer({ min: 1900, max: 2026 }),
  isAlive: fc.boolean(),
  placeOfBirth: fc.constantFrom(...locations),
  currentAddress: fc.constantFrom(...locations)
});

const filterArbitrary: fc.Arbitrary<MemberFilters> = fc.record({
  gender: fc.option(fc.constantFrom<Gender>('MALE', 'FEMALE', 'OTHER'), { nil: undefined }),
  generation: fc.option(fc.integer({ min: 0, max: 8 }), { nil: undefined }),
  birthYearFrom: fc.option(fc.integer({ min: 1900, max: 1960 }), { nil: undefined }),
  birthYearTo: fc.option(fc.integer({ min: 1961, max: 2026 }), { nil: undefined }),
  isAlive: fc.option(fc.boolean(), { nil: undefined }),
  location: fc.option(fc.constantFrom(...locations), { nil: undefined })
});

describe('Feature: family-genealogy-management, Property 11: Filter Returns Only Matching Members', () => {
  it('returns all and only members satisfying every applied condition', async () => {
    await fc.assert(
      fc.asyncProperty(
        fc.array(memberArbitrary, { maxLength: 60 }),
        filterArbitrary,
        async (records, filters) => {
          const members = records.map((record, index) => toMember(index, record));
          const service = new SearchService(async () => members);
          const actual = await service.filterMembers('tree-property', filters);
          const expected = members.filter((member) => independentlyMatches(member, filters));

          expect(actual.map((member) => member.id)).toEqual(expected.map((member) => member.id));
          expect(actual.every((member) => independentlyMatches(member, filters))).toBe(true);
        }
      ),
      { numRuns: 100 }
    );
  });
});

function toMember(
  index: number,
  record: {
    gender: Gender;
    generation: number;
    birthYear: number;
    isAlive: boolean;
    placeOfBirth: string;
    currentAddress: string;
  }
): Member {
  return {
    id: `member-${index}`,
    treeId: 'tree-property',
    firstName: 'Test',
    lastName: String(index),
    fullName: `Test ${index}`,
    gender: record.gender,
    generation: record.generation,
    dateOfBirth: `${record.birthYear}-01-01`,
    isAlive: record.isAlive,
    placeOfBirth: record.placeOfBirth,
    currentAddress: record.currentAddress,
    createdAt: '2026-01-01T00:00:00.000Z',
    updatedAt: '2026-01-01T00:00:00.000Z'
  };
}

function independentlyMatches(member: Member, filters: MemberFilters): boolean {
  if (filters.gender !== undefined && member.gender !== filters.gender) return false;
  if (filters.generation !== undefined && member.generation !== filters.generation) return false;
  const year = member.dateOfBirth ? Number(member.dateOfBirth.slice(0, 4)) : undefined;
  if (filters.birthYearFrom !== undefined && (year === undefined || year < filters.birthYearFrom)) return false;
  if (filters.birthYearTo !== undefined && (year === undefined || year > filters.birthYearTo)) return false;
  if (filters.isAlive !== undefined && member.isAlive !== filters.isAlive) return false;
  if (filters.location !== undefined) {
    const query = normalizeVietnamese(filters.location);
    const matchesLocation = [member.placeOfBirth, member.currentAddress]
      .filter((value): value is string => Boolean(value))
      .some((value) => normalizeVietnamese(value).includes(query));
    if (!matchesLocation) return false;
  }
  return true;
}
