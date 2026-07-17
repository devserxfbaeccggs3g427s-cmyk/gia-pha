import { NextResponse } from 'next/server';
import { getAlbums, getTrees } from '@/lib/blob/readers';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { eventMediaRouteError } from '@/lib/services/event-media-api-errors';
import { mediaService } from '@/lib/services/media-service';

export const runtime = 'nodejs';

interface RouteContext { params: { albumId: string } }

async function findAlbumTree(request: Request, albumId: string): Promise<string | null> {
  const requestedTreeId = new URL(request.url).searchParams.get('treeId');
  if (requestedTreeId) {
    return (await getAlbums(requestedTreeId)).some((album) => album.id === albumId) ? requestedTreeId : null;
  }
  for (const tree of await getTrees()) {
    if ((await getAlbums(tree.id)).some((album) => album.id === albumId)) return tree.id;
  }
  return null;
}

export async function PUT(request: Request, { params }: RouteContext): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    const treeId = await findAlbumTree(request, params.albumId);
    if (!treeId) return notFound();
    await requireTreePermission(treeId, userId, 'UPDATE');
    return NextResponse.json(
      await mediaService.updateAlbum(treeId, params.albumId, await request.json(), userId)
    );
  } catch (error) {
    return eventMediaRouteError(error, 'media');
  }
}

export async function DELETE(request: Request, { params }: RouteContext): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    const treeId = await findAlbumTree(request, params.albumId);
    if (!treeId) return notFound();
    await requireTreePermission(treeId, userId, 'DELETE');
    await mediaService.deleteAlbum(treeId, params.albumId, userId);
    return new NextResponse(null, { status: 204 });
  } catch (error) {
    return eventMediaRouteError(error, 'media');
  }
}

function notFound(): NextResponse {
  return NextResponse.json(
    { ok: false, error: { code: 'NOT_FOUND', message: 'Album not found' } },
    { status: 404 }
  );
}
