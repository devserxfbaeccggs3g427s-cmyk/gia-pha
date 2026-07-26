import { NextResponse } from 'next/server';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { serviceRouteError } from '@/lib/api/service-error-handler';
import { mediaService } from '@/lib/services/media-service';

export const runtime = 'nodejs';

interface RouteContext {
  params: Promise<{ mediaId: string }>;
}

async function resolveTreeId(request: Request): Promise<string | null> {
    return new URL(request.url).searchParams.get('treeId');
}

export async function GET(request: Request, { params }: RouteContext): Promise<NextResponse> {
    try {
        const userId = await requireAuthenticatedUserId();
        const treeId = await resolveTreeId(request);
        if (!treeId) {
            return NextResponse.json(
                { ok: false, error: { code: 'INVALID_INPUT', message: 'treeId query parameter is required' } },
                { status: 400 }
            );
        }
        const { requireTreePermission } = await import('@/lib/auth/rbac');
        await requireTreePermission(treeId, userId, 'READ');
        return NextResponse.json(await mediaService.getMedia(treeId, (await params).mediaId));
    } catch (error) {
        return serviceRouteError(error, 'Không thể truy xuất media');
    }
}

export async function DELETE(request: Request, { params }: RouteContext): Promise<NextResponse> {
    try {
        const userId = await requireAuthenticatedUserId();
        const treeId = await resolveTreeId(request);
        if (!treeId) {
            return NextResponse.json(
                { ok: false, error: { code: 'INVALID_INPUT', message: 'treeId query parameter is required' } },
                { status: 400 }
            );
        }
        const { requireTreePermission } = await import('@/lib/auth/rbac');
        await requireTreePermission(treeId, userId, 'DELETE');
        await mediaService.deleteMedia(treeId, (await params).mediaId);
        return new NextResponse(null, { status: 204 });
    } catch (error) {
        return serviceRouteError(error, 'Không thể xóa media');
    }
}
