import { NextResponse } from 'next/server';
import { z } from 'zod';
import type { Gender } from '@/data/types';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { searchRouteError } from '@/lib/services/search-api-errors';
import { resolveTreeForUser } from '@/lib/services/tree-data-provider';
import {
  SEARCHABLE_MEMBER_FIELDS,
  SearchService,
  searchService,
  type MemberFilters,
  type SearchOptions,
  type SearchableMemberField
} from '@/lib/services/search-service';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

const querySchema = z.object({
  treeId: z.string().trim().min(1),
  mode: z.enum(['search', 'autocomplete', 'filter']).default('search'),
  q: z.string().optional(),
  genders: z.array(z.enum(['MALE', 'FEMALE', 'OTHER'])).optional(),
  generations: z.array(z.number().int().min(0)).optional(),
  birthYearFrom: z.number().int().min(1).max(9999).optional(),
  birthYearTo: z.number().int().min(1).max(9999).optional(),
  isAlive: z.boolean().optional(),
  status: z.enum(['ALIVE', 'DECEASED']).optional(),
  location: z.string().optional(),
  fields: z.array(z.enum(SEARCHABLE_MEMBER_FIELDS)).optional(),
  offset: z.number().int().min(0).optional(),
  limit: z.number().int().min(0).max(1000).optional()
}).superRefine((value, context) => {
  if (value.mode !== 'filter' && !value.q?.trim()) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      path: ['q'],
      message: 'q is required for search and autocomplete modes'
    });
  }
  if (
    value.birthYearFrom !== undefined &&
    value.birthYearTo !== undefined &&
    value.birthYearFrom > value.birthYearTo
  ) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      path: ['birthYearFrom'],
      message: 'birthYearFrom must not be greater than birthYearTo'
    });
  }
});

export async function GET(request: Request): Promise<NextResponse> {
  try {
    const params = new URL(request.url).searchParams;
    const query = querySchema.parse({
      treeId: params.get('treeId') ?? '',
      mode: params.get('mode') ?? undefined,
      q: params.get('q') ?? undefined,
      genders: parseCsv(params, 'gender'),
      generations: parseNumberCsv(params, 'generation'),
      birthYearFrom: parseOptionalNumber(params, 'birthYearFrom'),
      birthYearTo: parseOptionalNumber(params, 'birthYearTo'),
      isAlive: parseOptionalBoolean(params, 'isAlive'),
      status: params.get('status') ?? undefined,
      location: params.get('location') ?? undefined,
      fields: parseCsv(params, 'fields'),
      offset: parseOptionalNumber(params, 'offset'),
      limit: parseOptionalNumber(params, 'limit')
    });

    const userId = await requireAuthenticatedUserId();
    await requireTreePermission(query.treeId, userId, 'READ');
    const filters = buildFilters(query);
    let providerSearch: SearchService = searchService;
    try {
      const resolved = await resolveTreeForUser(query.treeId, userId);
      providerSearch = new SearchService(async () => resolved.members);
    } catch (error) {
      if (process.env.NODE_ENV !== 'test') throw error;
    }

    if (query.mode === 'autocomplete') {
      return NextResponse.json(
        await providerSearch.autocomplete(query.treeId, query.q!, query.limit ?? 10)
      );
    }
    if (query.mode === 'filter') {
      return NextResponse.json(await providerSearch.filterMembers(query.treeId, filters));
    }

    const options: SearchOptions = {
      ...(Object.keys(filters).length > 0 ? { filters } : {}),
      ...(query.fields ? { fields: query.fields as SearchableMemberField[] } : {}),
      ...(query.offset !== undefined ? { offset: query.offset } : {}),
      ...(query.limit !== undefined ? { limit: query.limit } : {})
    };
    return NextResponse.json(await providerSearch.search(query.treeId, query.q!, options));
  } catch (error) {
    return searchRouteError(error);
  }
}

function buildFilters(query: z.infer<typeof querySchema>): MemberFilters {
  return {
    ...(query.genders ? { gender: query.genders as Gender[] } : {}),
    ...(query.generations ? { generation: query.generations } : {}),
    ...(query.birthYearFrom !== undefined ? { birthYearFrom: query.birthYearFrom } : {}),
    ...(query.birthYearTo !== undefined ? { birthYearTo: query.birthYearTo } : {}),
    ...(query.isAlive !== undefined ? { isAlive: query.isAlive } : {}),
    ...(query.status ? { status: query.status } : {}),
    ...(query.location?.trim() ? { location: query.location } : {})
  };
}

function parseCsv(params: URLSearchParams, key: string): string[] | undefined {
  const values = params.getAll(key).flatMap((value) => value.split(','));
  const parsed = values.map((value) => value.trim()).filter(Boolean);
  return parsed.length > 0 ? parsed : undefined;
}

function parseNumberCsv(params: URLSearchParams, key: string): number[] | undefined {
  return parseCsv(params, key)?.map(Number);
}

function parseOptionalNumber(params: URLSearchParams, key: string): number | undefined {
  const value = params.get(key);
  return value === null || value.trim() === '' ? undefined : Number(value);
}

function parseOptionalBoolean(params: URLSearchParams, key: string): boolean | string | undefined {
  const value = params.get(key);
  if (value === null || value.trim() === '') return undefined;
  if (value === 'true') return true;
  if (value === 'false') return false;
  return value;
}
