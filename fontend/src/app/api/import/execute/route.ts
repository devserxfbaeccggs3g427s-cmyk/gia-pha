import { NextResponse } from 'next/server';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { serviceRouteError } from '@/lib/api/service-error-handler';
import { importService } from '@/lib/services/import-service';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

/**
 * Import execute endpoint. Per Task 29.4: parse + apply happen in one
 * transaction inside the microservice. The microservice returns a
 * job id which the BFF returns to the client for status polling.
 */
export async function POST(request: Request): Promise<NextResponse> {
    try {
        const userId = await requireAuthenticatedUserId();
        const body = await request.json();
        const treeId: string = body.treeId;
        const mode: 'append' | 'replace' = body.mode ?? 'append';
        const remapStrategy: 'skip' | 'overwrite' | 'regenerate' = body.conflictStrategy ?? 'skip';

        if (!treeId) {
            return NextResponse.json(
                { ok: false, error: { code: 'INVALID_INPUT', message: 'treeId là bắt buộc' } },
                { status: 400 }
            );
        }
        await requireTreePermission(treeId, userId, 'UPDATE');

        const job = await importService.execute({
            jobId: body.jobId ?? '',
            treeId,
            mode,
            remapStrategy
        });
        return NextResponse.json({
            ok: true,
            treeId,
            jobId: job.jobId,
            status: job.status,
            pollUrl: `/api/transfer/jobs/${job.jobId}`
        });
    } catch (error) {
        return serviceRouteError(error, 'Không thể thực thi import');
    }
}
