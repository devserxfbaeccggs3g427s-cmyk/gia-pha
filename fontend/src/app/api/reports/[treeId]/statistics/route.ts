import { NextResponse } from 'next/server';
import { z } from 'zod';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { serviceRouteError } from '@/lib/api/service-error-handler';
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
    { params }: { params: Promise<{ treeId: string }> }
): Promise<Response> {
    try {
        const { treeId } = await params;
        const queryParams = new URL(request.url).searchParams;
        const query = querySchema.parse({
            view: queryParams.get('view') ?? undefined,
            branchRootMemberId: queryParams.get('branchRootMemberId') ?? undefined,
            format: queryParams.get('format')?.toLowerCase() ?? undefined
        });
        const userId = await requireAuthenticatedUserId();
        await requireTreePermission(treeId, userId, 'READ');

        const statistics = await reportService.getStatistics(treeId);
        if (query.format === 'pdf') {
            const job = await reportService.requestReport(treeId, 'pdf');
            return NextResponse.json(
                { ok: true, format: 'pdf', jobId: job.jobId, status: job.status },
                { status: 202, headers: { 'Cache-Control': 'private, no-store' } }
            );
        }
        return NextResponse.json(
            query.view === 'timeline' ? { timeline: statistics.timeline ?? [] } : statistics,
            { headers: { 'Cache-Control': 'private, no-store' } }
        );
    } catch (error) {
        return serviceRouteError(error, 'Không thể truy xuất thống kê');
    }
}
