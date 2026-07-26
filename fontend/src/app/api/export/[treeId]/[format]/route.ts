import { NextResponse } from 'next/server';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { serviceRouteError } from '@/lib/api/service-error-handler';
import { exportService } from '@/lib/services/export-service';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

/**
 * Legacy export endpoint. The microservice decomposition splits this
 * into:
 * <ul>
 *   <li>Synchronous formats under 5 MiB / 8 s budget (Task 30) —
 *       streamed back through the gateway.</li>
 *   <li>V2 job API (Task 31) — used for heavier formats like PDF
 *       with embedded photos.</li>
 * </ul>
 *
 * <p>This BFF returns the job id for any non-trivial format so the
 * client polls the V2 job API.
 */
export async function GET(
    request: Request,
    { params }: { params: Promise<{ treeId: string; format: string }> }
): Promise<Response> {
    try {
        const userId = await requireAuthenticatedUserId();
        const { treeId, format } = await params;
        await requireTreePermission(treeId, userId, 'READ');
        const normalizedFormat = format.toLowerCase();
        if (!['gedcom', 'ged', 'json', 'pdf', 'png', 'image', 'svg'].includes(normalizedFormat)) {
            return NextResponse.json(
                { ok: false, error: { code: 'INVALID_INPUT', message: 'format không hợp lệ' } },
                { status: 400 }
            );
        }
        const normalized = normalizedFormat === 'ged' ? 'gedcom' : normalizedFormat === 'image' ? 'png' : (normalizedFormat as 'json' | 'pdf' | 'png' | 'svg' | 'gedcom');
        const job = await exportService.requestJob(treeId, normalized);
        return NextResponse.json(
            {
                ok: true,
                treeId,
                format: normalized,
                jobId: job.jobId,
                status: 'PENDING',
                pollUrl: `/api/transfer/jobs/${job.jobId}`
            },
            { status: 202 }
        );
    } catch (error) {
        return serviceRouteError(error, 'Không thể xuất bản');
    }
}
