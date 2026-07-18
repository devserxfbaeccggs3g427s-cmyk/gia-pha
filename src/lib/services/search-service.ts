import type { Gender, Member } from '@/data/types';
import { getMembers } from '@/lib/blob/readers';
import { normalizeVietnamese } from '@/lib/utils/vietnamese';

export const SEARCHABLE_MEMBER_FIELDS = [
  'fullName',
  'nickname',
  'occupation',
  'placeOfBirth'
] as const;

export type SearchableMemberField = (typeof SEARCHABLE_MEMBER_FIELDS)[number];

export interface MemberFilters {
  gender?: Gender | readonly Gender[];
  generation?: number | readonly number[];
  birthYearFrom?: number;
  birthYearTo?: number;
  birthYearRange?: {
    from?: number;
    to?: number;
    min?: number;
    max?: number;
  };
  isAlive?: boolean;
  aliveStatus?: 'ALIVE' | 'DECEASED';
  /** Alias accepted for clients that expose the member status as a select. */
  status?: 'ALIVE' | 'DECEASED';
  /** Accent-insensitive substring match against birthplace or current address. */
  location?: string;
}

export interface SearchOptions {
  fields?: readonly SearchableMemberField[];
  filters?: MemberFilters;
  offset?: number;
  limit?: number;
}

export interface SearchResult {
  member: Member;
  matchedFields: SearchableMemberField[];
  /** Relevance score; higher values are returned first. */
  score: number;
}

export interface AutocompleteItem {
  id: string;
  memberId: string;
  label: string;
  fullName: string;
  nickname?: string;
  avatarMediaId?: string;
  avatarUrl?: string;
}

export class SearchServiceError extends Error {
  constructor(
    public readonly code: 'INVALID_INPUT' | 'INVALID_FILTER',
    message: string
  ) {
    super(message);
    this.name = 'SearchServiceError';
  }
}

type MemberLoader = (treeId: string) => Promise<Member[]>;

interface RankedSearchResult extends SearchResult {
  sourceIndex: number;
}

export class SearchService {
  constructor(private readonly loadMembers: MemberLoader = getMembers) {}

  async search(
    treeId: string,
    query: string,
    options: SearchOptions = {}
  ): Promise<SearchResult[]> {
    assertTreeId(treeId);
    const normalizedQuery = normalizeVietnamese(query);
    if (!normalizedQuery) return [];

    const fields = validateSearchFields(options.fields);
    const pagination = validatePagination(options);
    const members = options.filters
      ? filterMemberCollection(await this.loadMembers(treeId), options.filters)
      : await this.loadMembers(treeId);

    const ranked: RankedSearchResult[] = [];
    members.forEach((member, sourceIndex) => {
      const matches = findMatches(member, normalizedQuery, fields);
      if (matches.length === 0) return;
      ranked.push({
        member,
        matchedFields: matches.map((match) => match.field),
        score: Math.max(...matches.map((match) => match.score)),
        sourceIndex
      });
    });

    ranked.sort((left, right) => right.score - left.score || left.sourceIndex - right.sourceIndex);
    return ranked
      .slice(pagination.offset, pagination.limit === undefined ? undefined : pagination.offset + pagination.limit)
      .map(({ sourceIndex: _sourceIndex, ...result }) => result);
  }

  async autocomplete(
    treeId: string,
    prefix: string,
    limit = 10
  ): Promise<AutocompleteItem[]> {
    assertTreeId(treeId);
    const normalizedPrefix = normalizeVietnamese(prefix);
    if (normalizedPrefix.length < 2) return [];
    assertNonNegativeInteger(limit, 'limit');
    if (limit === 0) return [];

    const members = await this.loadMembers(treeId);
    return members
      .map((member, sourceIndex) => ({
        member,
        sourceIndex,
        rank: autocompleteRank(member, normalizedPrefix)
      }))
      .filter((candidate) => candidate.rank > 0)
      .sort((left, right) => right.rank - left.rank || left.sourceIndex - right.sourceIndex)
      .slice(0, limit)
      .map(({ member }) => ({
        id: member.id,
        memberId: member.id,
        label: member.nickname
          ? `${member.fullName} (${member.nickname})`
          : member.fullName,
        fullName: member.fullName,
        ...(member.nickname ? { nickname: member.nickname } : {}),
        ...(member.avatarMediaId ? { avatarMediaId: member.avatarMediaId } : {}),
        ...(member.avatarUrl ? { avatarUrl: member.avatarUrl } : {})
      }));
  }

  async filterMembers(treeId: string, filters: MemberFilters): Promise<Member[]> {
    assertTreeId(treeId);
    return filterMemberCollection(await this.loadMembers(treeId), filters);
  }
}

interface FieldMatch {
  field: SearchableMemberField;
  score: number;
}

function findMatches(
  member: Member,
  query: string,
  fields: readonly SearchableMemberField[]
): FieldMatch[] {
  const matches: FieldMatch[] = [];
  for (const field of fields) {
    const rawValue = member[field];
    if (!rawValue) continue;
    const value = normalizeVietnamese(rawValue);
    const position = value.indexOf(query);
    if (position < 0) continue;

    const fieldWeight = field === 'fullName' ? 30 : field === 'nickname' ? 20 : 10;
    const matchWeight = value === query ? 100 : position === 0 ? 70 : isWordStart(value, position) ? 50 : 30;
    matches.push({ field, score: fieldWeight + matchWeight });
  }
  return matches;
}

function autocompleteRank(member: Member, prefix: string): number {
  const fullName = normalizeVietnamese(member.fullName);
  const nickname = member.nickname ? normalizeVietnamese(member.nickname) : '';
  if (fullName === prefix) return 140;
  if (nickname === prefix) return 135;
  if (fullName.startsWith(prefix)) return 120;
  if (nickname.startsWith(prefix)) return 115;
  if (hasWordWithPrefix(fullName, prefix)) return 100;
  if (hasWordWithPrefix(nickname, prefix)) return 95;
  return 0;
}

function hasWordWithPrefix(value: string, prefix: string): boolean {
  return value.split(' ').some((word) => word.startsWith(prefix));
}

function isWordStart(value: string, position: number): boolean {
  return position === 0 || /\s/.test(value[position - 1]);
}

export function filterMemberCollection(
  members: readonly Member[],
  filters: MemberFilters
): Member[] {
  const normalized = normalizeFilters(filters);
  return members.filter((member) => {
    if (normalized.genders && !normalized.genders.has(member.gender)) return false;
    if (normalized.generations && (member.generation === undefined || !normalized.generations.has(member.generation))) {
      return false;
    }

    if (normalized.birthYearFrom !== undefined || normalized.birthYearTo !== undefined) {
      const birthYear = getBirthYear(member.dateOfBirth);
      if (birthYear === undefined) return false;
      if (normalized.birthYearFrom !== undefined && birthYear < normalized.birthYearFrom) return false;
      if (normalized.birthYearTo !== undefined && birthYear > normalized.birthYearTo) return false;
    }

    if (normalized.isAlive !== undefined && member.isAlive !== normalized.isAlive) return false;
    if (normalized.location) {
      const locations = [member.placeOfBirth, member.currentAddress]
        .filter((value): value is string => Boolean(value))
        .map(normalizeVietnamese);
      if (!locations.some((location) => location.includes(normalized.location!))) return false;
    }
    return true;
  });
}

interface NormalizedFilters {
  genders?: ReadonlySet<Gender>;
  generations?: ReadonlySet<number>;
  birthYearFrom?: number;
  birthYearTo?: number;
  isAlive?: boolean;
  location?: string;
}

function normalizeFilters(filters: MemberFilters): NormalizedFilters {
  const genders = filters.gender === undefined
    ? undefined
    : new Set(Array.isArray(filters.gender) ? filters.gender : [filters.gender]);
  const generations = filters.generation === undefined
    ? undefined
    : new Set(Array.isArray(filters.generation) ? filters.generation : [filters.generation]);
  const birthYearFrom = filters.birthYearFrom ?? filters.birthYearRange?.from ?? filters.birthYearRange?.min;
  const birthYearTo = filters.birthYearTo ?? filters.birthYearRange?.to ?? filters.birthYearRange?.max;

  if (genders?.size === 0) throw new SearchServiceError('INVALID_FILTER', 'gender must not be empty');
  for (const gender of genders ?? []) {
    if (!['MALE', 'FEMALE', 'OTHER'].includes(gender)) {
      throw new SearchServiceError('INVALID_FILTER', 'gender is invalid');
    }
  }
  if (generations?.size === 0) throw new SearchServiceError('INVALID_FILTER', 'generation must not be empty');
  for (const generation of generations ?? []) {
    assertNonNegativeInteger(generation, 'generation', 'INVALID_FILTER');
  }
  if (birthYearFrom !== undefined) assertYear(birthYearFrom, 'birthYearFrom');
  if (birthYearTo !== undefined) assertYear(birthYearTo, 'birthYearTo');
  if (birthYearFrom !== undefined && birthYearTo !== undefined && birthYearFrom > birthYearTo) {
    throw new SearchServiceError('INVALID_FILTER', 'birthYearFrom must not be greater than birthYearTo');
  }

  const statuses = [filters.aliveStatus, filters.status].filter(
    (status): status is 'ALIVE' | 'DECEASED' => status !== undefined
  );
  const statusValue = statuses[0] === undefined ? undefined : statuses[0] === 'ALIVE';
  if (statuses.some((status) => (status === 'ALIVE') !== statusValue)) {
    throw new SearchServiceError('INVALID_FILTER', 'alive status filters conflict');
  }
  if (filters.isAlive !== undefined && statusValue !== undefined && filters.isAlive !== statusValue) {
    throw new SearchServiceError('INVALID_FILTER', 'alive status filters conflict');
  }

  return {
    ...(genders ? { genders } : {}),
    ...(generations ? { generations } : {}),
    ...(birthYearFrom !== undefined ? { birthYearFrom } : {}),
    ...(birthYearTo !== undefined ? { birthYearTo } : {}),
    ...(filters.isAlive !== undefined || statusValue !== undefined
      ? { isAlive: filters.isAlive ?? statusValue }
      : {}),
    ...(filters.location && normalizeVietnamese(filters.location)
      ? { location: normalizeVietnamese(filters.location) }
      : {})
  };
}

function getBirthYear(dateOfBirth?: string): number | undefined {
  if (!dateOfBirth || !/^\d{4}-\d{2}-\d{2}(?:T.*)?$/.test(dateOfBirth)) return undefined;
  const parsed = new Date(dateOfBirth);
  if (Number.isNaN(parsed.getTime())) return undefined;
  return Number(dateOfBirth.slice(0, 4));
}

function validateSearchFields(
  fields: readonly SearchableMemberField[] | undefined
): readonly SearchableMemberField[] {
  if (fields === undefined) return SEARCHABLE_MEMBER_FIELDS;
  if (fields.length === 0) throw new SearchServiceError('INVALID_INPUT', 'fields must not be empty');
  for (const field of fields) {
    if (!(SEARCHABLE_MEMBER_FIELDS as readonly string[]).includes(field)) {
      throw new SearchServiceError('INVALID_INPUT', `Unsupported search field: ${field}`);
    }
  }
  return [...new Set(fields)];
}

function validatePagination(options: SearchOptions): { offset: number; limit?: number } {
  const offset = options.offset ?? 0;
  assertNonNegativeInteger(offset, 'offset');
  if (options.limit !== undefined) assertNonNegativeInteger(options.limit, 'limit');
  return options.limit === undefined ? { offset } : { offset, limit: options.limit };
}

function assertTreeId(treeId: string): void {
  if (!treeId?.trim()) throw new SearchServiceError('INVALID_INPUT', 'treeId is required');
}

function assertNonNegativeInteger(
  value: number,
  field: string,
  code: SearchServiceError['code'] = 'INVALID_INPUT'
): void {
  if (!Number.isInteger(value) || value < 0) {
    throw new SearchServiceError(code, `${field} must be a non-negative integer`);
  }
}

function assertYear(value: number, field: string): void {
  if (!Number.isInteger(value) || value < 1 || value > 9999) {
    throw new SearchServiceError('INVALID_FILTER', `${field} must be a valid year`);
  }
}

export const searchService = new SearchService();
export default searchService;

export const search = searchService.search.bind(searchService);
export const autocomplete = searchService.autocomplete.bind(searchService);
export const filterMembers = searchService.filterMembers.bind(searchService);
