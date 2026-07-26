import { describe, expect, it } from 'vitest';
import type { Member } from '@/data/types';
import { SearchService, SearchServiceError } from '@/lib/services/search-service';
import { buildMember } from '../../utils/factories';

describe('SearchService', () => {
  const members: Member[] = [
    buildMember({
      id: 'name',
      fullName: 'Nguyễn Văn Đạt',
      nickname: 'Tí',
      gender: 'MALE',
      generation: 2,
      dateOfBirth: '1985-06-12',
      placeOfBirth: 'Thừa Thiên Huế',
      currentAddress: 'Đà Nẵng',
      occupation: 'Kỹ sư',
      isAlive: true
    }),
    buildMember({
      id: 'occupation',
      fullName: 'Trần Thị Mai',
      gender: 'FEMALE',
      generation: 3,
      dateOfBirth: '1994-01-01',
      placeOfBirth: 'Hà Nội',
      occupation: 'Bác sĩ',
      isAlive: true
    }),
    buildMember({
      id: 'address-only',
      fullName: 'Lê Minh An',
      gender: 'OTHER',
      generation: 2,
      dateOfBirth: '1970-01-01',
      currentAddress: 'Hải Phòng',
      isAlive: false
    })
  ];

  const service = new SearchService(async () => members);

  it('searches all specified fields without Vietnamese diacritics and reports match metadata', async () => {
    await expect(service.search('tree-1', 'nguyen van dat')).resolves.toEqual([
      expect.objectContaining({
        member: expect.objectContaining({ id: 'name' }),
        matchedFields: ['fullName'],
        score: expect.any(Number)
      })
    ]);

    const byOccupation = await service.search('tree-1', 'bac si');
    expect(byOccupation.map((result) => result.member.id)).toEqual(['occupation']);
    const byBirthplace = await service.search('tree-1', 'thua thien');
    expect(byBirthplace.map((result) => result.member.id)).toEqual(['name']);
    await expect(service.search('tree-1', 'hai phong')).resolves.toEqual([]);
  });

  it('returns deterministic relevance ordering and supports pagination', async () => {
    const rankedService = new SearchService(async () => [
      buildMember({ id: 'contains', fullName: 'Nguyễn Văn An' }),
      buildMember({ id: 'exact', fullName: 'An' }),
      buildMember({ id: 'prefix', fullName: 'An Bình' })
    ]);

    const results = await rankedService.search('tree-1', 'an', { offset: 1, limit: 1 });
    expect(results.map((result) => result.member.id)).toEqual(['prefix']);
  });

  it('provides accent-insensitive autocomplete only after two characters', async () => {
    await expect(service.autocomplete('tree-1', 'đ')).resolves.toEqual([]);
    const suggestions = await service.autocomplete('tree-1', 'Dat');
    expect(suggestions).toEqual([
      expect.objectContaining({ id: 'name', memberId: 'name', fullName: 'Nguyễn Văn Đạt' })
    ]);
  });

  it('combines all member filters with AND logic and inclusive year boundaries', async () => {
    const result = await service.filterMembers('tree-1', {
      gender: 'MALE',
      generation: 2,
      birthYearFrom: 1985,
      birthYearTo: 1985,
      isAlive: true,
      location: 'da nang'
    });
    expect(result.map((member) => member.id)).toEqual(['name']);

    await expect(service.filterMembers('tree-1', { location: 'Hai Phong' })).resolves.toEqual([
      expect.objectContaining({ id: 'address-only' })
    ]);
  });

  it('rejects contradictory or malformed filter and pagination values', async () => {
    await expect(service.filterMembers('tree-1', { birthYearFrom: 2000, birthYearTo: 1999 }))
      .rejects.toEqual(expect.objectContaining({ code: 'INVALID_FILTER' } satisfies Partial<SearchServiceError>));
    await expect(service.filterMembers('tree-1', { isAlive: true, status: 'DECEASED' }))
      .rejects.toEqual(expect.objectContaining({ code: 'INVALID_FILTER' } satisfies Partial<SearchServiceError>));
    await expect(service.search('tree-1', 'an', { limit: -1 }))
      .rejects.toEqual(expect.objectContaining({ code: 'INVALID_INPUT' } satisfies Partial<SearchServiceError>));
  });

  it('searches a typical 1,000-member in-memory dataset within 500ms', async () => {
    const dataset = Array.from({ length: 1000 }, (_, index) =>
      buildMember({ id: `member-${index}`, fullName: `Thành viên ${index}` })
    );
    const performanceService = new SearchService(async () => dataset);
    const startedAt = performance.now();
    const results = await performanceService.search('tree-1', 'thanh vien');

    expect(results).toHaveLength(1000);
    expect(performance.now() - startedAt).toBeLessThan(500);
  });
});
