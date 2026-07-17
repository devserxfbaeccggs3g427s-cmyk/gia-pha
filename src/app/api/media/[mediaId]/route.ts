import { NextResponse } from 'next/server';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { eventMediaRouteError } from '@/lib/services/event-media-api-errors';
import { mediaService } from '@/lib/services/media-service';
import { findMediaTree } from '@/lib/services/media-route-utils';

export const runtime = 'nodejs';

interface RouteContext {
  params: { mediaId: string };
}

export async function GET(request: Request, { params }: RouteContext): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    const treeId = await findMediaTree(request, params.mediaId);
    if (!treeId) return notFound();
    await requireTreePermission(treeId, userId, 'READ');
    return NextResponse.json(await mediaService.getMedia(treeId, params.mediaId));
  } catch (error) {
    return eventMediaRouteError(error, 'media');
  }
}

export async function DELETE(request: Request, { params }: RouteContext): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    const treeId = await findMediaTree(request, params.mediaId);
    if (!treeId) return notFound();
    await requireTreePermission(treeId, userId, 'DELETE');
    await mediaService.deleteMedia(treeId, params.mediaId, userId);
    return new NextResponse(null, { status: 204 });
  } catch (error) {
    return eventMediaRouteError(error, 'media');
  }
}

function notFound(): NextResponse {
  return NextResponse.json(
    { ok: false, error: { code: 'NOT_FOUND', message: 'Media not found' } },
    { status: 404 }
  );
}
