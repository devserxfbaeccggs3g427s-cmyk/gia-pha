import { NextResponse } from 'next/server';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { eventMediaRouteError } from '@/lib/services/event-media-api-errors';
import { mediaService } from '@/lib/services/media-service';

export const runtime = 'nodejs';

export async function GET(
  _request: Request,
  { params }: { params: { treeId: string } }
): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    await requireTreePermission(params.treeId, userId, 'READ');
    return NextResponse.json(await mediaService.getAlbums(params.treeId));
  } catch (error) {
    return eventMediaRouteError(error, 'media');
  }
}

export async function POST(
  request: Request,
  { params }: { params: { treeId: string } }
): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    await requireTreePermission(params.treeId, userId, 'CREATE');
    return NextResponse.json(
      await mediaService.createAlbum(params.treeId, await request.json(), userId),
      { status: 201 }
    );
  } catch (error) {
    return eventMediaRouteError(error, 'media');
  }
}
