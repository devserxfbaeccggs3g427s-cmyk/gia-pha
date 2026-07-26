import { NextResponse } from 'next/server';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { serviceRouteError } from '@/lib/api/service-error-handler';
import { mediaService } from '@/lib/services/media-service';

export const runtime = 'nodejs';

interface RouteContext {
  params: Promise<{ mediaId: string }>;
}

/**
 * Resolve the binary blob URL for a media object. The legacy BFF returned
 * a redirect to a signed Blob URL; in the microservice decomposition the
 * gateway forwards to {@code binary-storage-service} which proxies the
 * signed Vercel Blob URL with the appropriate content-disposition.
 */
export async function GET(request: Request, { params }: RouteContext): Promise<NextResponse> {
    try {
        const userId = await requireAuthenticatedUserId();
        const treeId = new URL(request.url).searchParams.get('treeId');
        if (!treeId) {
            return NextResponse.json(
                { ok: false, error: { code: 'INVALID_INPUT', message: 'treeId query parameter is required' } },
                { status: 400 }
            );
        }
        await requireTreePermission(treeId, userId, 'READ');
        const media = await mediaService.getMedia(treeId, (await params).mediaId);
        return NextResponse.redirect(media.contentUrl ?? media.blobUrl, 302);
    } catch (error) {
        return serviceRouteError(error, 'Không thể truy xuất nội dung media');
    }
}
