import { NextResponse } from 'next/server';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { serviceRouteError } from '@/lib/api/service-error-handler';
import { backupService } from '@/lib/services/backup-service';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

interface RouteContext {
    params: Promise<{ treeId: string }>;
}

export async function GET(_request: Request, { params }: RouteContext): Promise<NextResponse> {
    try {
        const userId = await requireAuthenticatedUserId();
        const { treeId } = await params;
        await requireTreePermission(treeId, userId, 'READ');
        return NextResponse.json(
            { snapshots: await backupService.list(treeId), retentionDays: 30 },
            { headers: { 'Cache-Control': 'private, no-store' } }
        );
    } catch (error) {
        return serviceRouteError(error, 'Không thể truy xuất backup');
    }
}

export async function POST(_request: Request, { params }: RouteContext): Promise<NextResponse> {
    try {
        const userId = await requireAuthenticatedUserId();
        const { treeId } = await params;
        await requireTreePermission(treeId, userId, 'UPDATE');
        const snapshot = await backupService.create(treeId);
        return NextResponse.json(snapshot, {
            status: 201,
            headers: { 'Cache-Control': 'private, no-store' }
        });
    } catch (error) {
        return serviceRouteError(error, 'Không thể tạo backup');
    }
}

export async function PUT(request: Request, { params }: RouteContext): Promise<NextResponse> {
    try {
        const userId = await requireAuthenticatedUserId();
        const { treeId } = await params;
        await requireTreePermission(treeId, userId, 'UPDATE');
        const body = (await request.json()) as { snapshotId?: string; timestamp?: string };
        const snapshotId = body.snapshotId ?? body.timestamp;
        if (!snapshotId) {
            return NextResponse.json(
                { ok: false, error: { code: 'INVALID_INPUT', message: 'snapshotId là bắt buộc' } },
                { status: 400 }
            );
        }
        const job = await backupService.restore(treeId, snapshotId);
        return NextResponse.json(
            { ok: true, treeId, snapshotId, jobId: job.jobId },
            { headers: { 'Cache-Control': 'private, no-store' } }
        );
    } catch (error) {
        return serviceRouteError(error, 'Không thể khôi phục backup');
    }
}
