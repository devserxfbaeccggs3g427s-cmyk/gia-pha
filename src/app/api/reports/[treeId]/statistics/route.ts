import { NextResponse } from 'next/server';
import { z } from 'zod';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { reportRouteError } from '@/lib/services/report-api-errors';
import { reportService } from '@/lib/services/report-service';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

const querySchema = z.object({
  view: z.enum(['summary', 'timeline']).default('summary'),
  branchRootMemberId: z.string().trim().min(1).optional(),
  format: z.enum(['json', 'pdf']).default('json')
});

export async function GET(
  request: Request,
  { params }: { params: { treeId: string } }
): Promise<Response> {
  try {
    const queryParams = new URL(request.url).searchParams;
    const query = querySchema.parse({
      view: queryParams.get('view') ?? undefined,
      branchRootMemberId: queryParams.get('branchRootMemberId') ?? undefined,
      format: queryParams.get('format')?.toLowerCase() ?? undefined
    });
    const userId = await requireAuthenticatedUserId();
    await requireTreePermission(params.treeId, userId, 'READ');

    if (query.format === 'pdf') {
      const pdf = await reportService.exportPDF(params.treeId, query.branchRootMemberId);
      return new NextResponse(new Uint8Array(pdf), {
        status: 200,
        headers: {
          'Content-Type': 'application/pdf',
          'Content-Disposition': `attachment; filename="${safeFilename(params.treeId)}-statistics.pdf"`,
          'Cache-Control': 'private, no-store'
        }
      });
    }
    if (query.view === 'timeline') {
      const timeline = query.branchRootMemberId
        ? await reportService.getGrowthTimeline(params.treeId, query.branchRootMemberId)
        : await reportService.getGrowthTimeline(params.treeId);
      return NextResponse.json(timeline, {
        headers: { 'Cache-Control': 'private, no-store' }
      });
    }
    const statistics = query.branchRootMemberId
      ? await reportService.getBranchStatistics(params.treeId, query.branchRootMemberId)
      : await reportService.getStatistics(params.treeId);
    return NextResponse.json(statistics, { headers: { 'Cache-Control': 'private, no-store' } });
  } catch (error) {
    return reportRouteError(error);
  }
}

function safeFilename(value: string): string {
  return value.replace(/[^a-zA-Z0-9_-]/g, '_').slice(0, 100) || 'family-tree';
}
