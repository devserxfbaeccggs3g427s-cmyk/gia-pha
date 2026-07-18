import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/lib/auth/guards', () => ({
  AuthenticationError: class AuthenticationError extends Error {
    constructor() { super('Authentication is required'); this.name = 'AuthenticationError'; }
  },
  requireAuthenticatedUserId: vi.fn(async () => 'user-1')
}));

vi.mock('@/lib/auth/rbac', () => ({
  AuthorizationError: class AuthorizationError extends Error {
    constructor(public readonly code: 'TREE_NOT_FOUND' | 'FORBIDDEN', message: string) {
      super(message); this.name = 'AuthorizationError';
    }
  },
  requireTreePermission: vi.fn(async () => ({ role: 'VIEWER' }))
}));

import { GET } from '@/app/api/reports/[treeId]/statistics/route';
import { AuthenticationError, requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { reportService } from '@/lib/services/report-service';

describe('GET /api/reports/[treeId]/statistics', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.mocked(requireAuthenticatedUserId).mockResolvedValue('user-1');
    vi.mocked(requireTreePermission).mockResolvedValue({ role: 'VIEWER' } as never);
  });

  it('checks READ permission and returns whole-tree statistics by default', async () => {
    const getStatistics = vi.spyOn(reportService, 'getStatistics').mockResolvedValue({ totalMembers: 12 } as never);
    const response = await GET(
      new Request('http://localhost/api/reports/tree-1/statistics'),
      { params: { treeId: 'tree-1' } }
    );

    expect(response.status).toBe(200);
    expect(requireTreePermission).toHaveBeenCalledWith('tree-1', 'user-1', 'READ');
    expect(getStatistics).toHaveBeenCalledWith('tree-1');
    await expect(response.json()).resolves.toMatchObject({ totalMembers: 12 });
  });

  it('supports branch statistics, timeline data, and PDF downloads', async () => {
    const getBranchStatistics = vi.spyOn(reportService, 'getBranchStatistics').mockResolvedValue({ totalMembers: 4 } as never);
    const branchResponse = await GET(
      new Request('http://localhost/api/reports/tree-1/statistics?branchRootMemberId=root'),
      { params: { treeId: 'tree-1' } }
    );
    expect(branchResponse.status).toBe(200);
    expect(getBranchStatistics).toHaveBeenCalledWith('tree-1', 'root');

    const getGrowthTimeline = vi.spyOn(reportService, 'getGrowthTimeline').mockResolvedValue([
      { period: '2024-01', newMembers: 2, totalMembers: 2 }
    ]);
    const timelineResponse = await GET(
      new Request('http://localhost/api/reports/tree-1/statistics?view=timeline'),
      { params: { treeId: 'tree-1' } }
    );
    expect(timelineResponse.status).toBe(200);
    expect(getGrowthTimeline).toHaveBeenCalledWith('tree-1');

    const exportPDF = vi.spyOn(reportService, 'exportPDF').mockResolvedValue(Buffer.from('%PDF-test'));
    const pdfResponse = await GET(
      new Request('http://localhost/api/reports/tree-1/statistics?format=pdf&branchRootMemberId=root'),
      { params: { treeId: 'tree-1' } }
    );
    expect(pdfResponse.status).toBe(200);
    expect(pdfResponse.headers.get('content-type')).toBe('application/pdf');
    expect(exportPDF).toHaveBeenCalledWith('tree-1', 'root');
  });

  it('returns structured validation and authentication failures', async () => {
    const invalidResponse = await GET(
      new Request('http://localhost/api/reports/tree-1/statistics?view=unknown'),
      { params: { treeId: 'tree-1' } }
    );
    expect(invalidResponse.status).toBe(400);
    await expect(invalidResponse.json()).resolves.toMatchObject({ ok: false, error: { code: 'VALIDATION_ERROR' } });

    vi.mocked(requireAuthenticatedUserId).mockRejectedValueOnce(new AuthenticationError());
    const unauthenticatedResponse = await GET(
      new Request('http://localhost/api/reports/tree-1/statistics'),
      { params: { treeId: 'tree-1' } }
    );
    expect(unauthenticatedResponse.status).toBe(401);
    await expect(unauthenticatedResponse.json()).resolves.toMatchObject({ ok: false, error: { code: 'UNAUTHENTICATED' } });
  });
});

