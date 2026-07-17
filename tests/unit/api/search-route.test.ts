import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/lib/auth/guards', () => ({
  AuthenticationError: class AuthenticationError extends Error {
    constructor() {
      super('Authentication is required');
      this.name = 'AuthenticationError';
    }
  },
  requireAuthenticatedUserId: vi.fn(async () => 'user-1')
}));

vi.mock('@/lib/auth/rbac', () => ({
  AuthorizationError: class AuthorizationError extends Error {
    constructor(public readonly code: 'TREE_NOT_FOUND' | 'FORBIDDEN', message: string) {
      super(message);
      this.name = 'AuthorizationError';
    }
  },
  requireTreePermission: vi.fn(async () => ({ role: 'VIEWER' }))
}));

import { GET } from '@/app/api/search/route';
import { AuthenticationError, requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { searchService } from '@/lib/services/search-service';

describe('GET /api/search', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.mocked(requireAuthenticatedUserId).mockResolvedValue('user-1');
    vi.mocked(requireTreePermission).mockResolvedValue({ role: 'VIEWER' } as never);
  });

  it('validates access and maps search plus filter query parameters', async () => {
    const search = vi.spyOn(searchService, 'search').mockResolvedValue([]);
    const response = await GET(new Request(
      'http://localhost/api/search?treeId=tree-1&q=nguyen&gender=MALE&generation=2&birthYearFrom=1980&isAlive=true&location=Da%20Nang&limit=20'
    ));

    expect(response.status).toBe(200);
    expect(requireTreePermission).toHaveBeenCalledWith('tree-1', 'user-1', 'READ');
    expect(search).toHaveBeenCalledWith('tree-1', 'nguyen', {
      filters: {
        gender: ['MALE'],
        generation: [2],
        birthYearFrom: 1980,
        isAlive: true,
        location: 'Da Nang'
      },
      limit: 20
    });
  });

  it('supports autocomplete and filter modes', async () => {
    const autocomplete = vi.spyOn(searchService, 'autocomplete').mockResolvedValue([]);
    const filterMembers = vi.spyOn(searchService, 'filterMembers').mockResolvedValue([]);

    const autocompleteResponse = await GET(new Request(
      'http://localhost/api/search?treeId=tree-1&mode=autocomplete&q=ng&limit=5'
    ));
    const filterResponse = await GET(new Request(
      'http://localhost/api/search?treeId=tree-1&mode=filter&status=DECEASED'
    ));

    expect(autocompleteResponse.status).toBe(200);
    expect(autocomplete).toHaveBeenCalledWith('tree-1', 'ng', 5);
    expect(filterResponse.status).toBe(200);
    expect(filterMembers).toHaveBeenCalledWith('tree-1', { status: 'DECEASED' });
  });

  it('returns structured 400 responses for invalid query parameters', async () => {
    const search = vi.spyOn(searchService, 'search');
    const response = await GET(new Request(
      'http://localhost/api/search?treeId=tree-1&gender=UNKNOWN&birthYearFrom=2020&birthYearTo=1990'
    ));

    expect(response.status).toBe(400);
    await expect(response.json()).resolves.toMatchObject({
      ok: false,
      error: { code: 'VALIDATION_ERROR' }
    });
    expect(search).not.toHaveBeenCalled();
  });

  it('does not expose tree data to unauthenticated callers', async () => {
    vi.mocked(requireAuthenticatedUserId).mockRejectedValueOnce(new AuthenticationError());
    const response = await GET(new Request(
      'http://localhost/api/search?treeId=tree-1&q=nguyen'
    ));

    expect(response.status).toBe(401);
    await expect(response.json()).resolves.toMatchObject({
      ok: false,
      error: { code: 'UNAUTHENTICATED' }
    });
  });
});
