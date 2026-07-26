import { NextResponse } from 'next/server';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { serviceRouteError } from '@/lib/api/service-error-handler';
import { mediaService } from '@/lib/services/media-service';

export const runtime = 'nodejs';

interface RouteContext { params: Promise<{ albumId: string }> }

/**
 * Album detail endpoint. The tree scope is passed via {@code ?treeId}
 * query parameter (the legacy URL contract).
 */
async function resolveTreeId(request: Request): Promise<string | null> {
    const url = new URL(request.url);
    const treeId = url.searchParams.get('treeId');
    return treeId ?? null;
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
        return NextResponse.json(await mediaService.getAlbum(treeId, (await params).albumId));
    } catch (error) {
        return serviceRouteError(error, 'Không thể truy xuất album');
    }
}

export async function PUT(request: Request, { params }: RouteContext): Promise<NextResponse> {
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
        await requireTreePermission(treeId, userId, 'UPDATE');
        return NextResponse.json(
            await mediaService.updateAlbum(treeId, (await params).albumId, await request.json(), userId)
        );
    } catch (error) {
        return serviceRouteError(error, 'Không thể cập nhật album');
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
        await mediaService.deleteAlbum(treeId, (await params).albumId, userId);
        return new NextResponse(null, { status: 204 });
    } catch (error) {
        return serviceRouteError(error, 'Không thể xóa album');
    }
}
