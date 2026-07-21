import { NextResponse } from 'next/server';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { eventMediaRouteError } from '@/lib/services/event-media-api-errors';
import { mediaService } from '@/lib/services/media-service';
import { resolveTreeForUser } from '@/lib/services/tree-data-provider';

export const runtime = 'nodejs';

export async function GET(
  request: Request,
  { params }: { params: { treeId: string } }
): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    await requireTreePermission(params.treeId, userId, 'READ');
    const query = new URL(request.url).searchParams;
    const memberId = query.get('memberId');
    const eventId = query.get('eventId');
    const albumId = query.get('albumId');
    const activeFilters = [memberId, eventId, albumId].filter(Boolean).length;
    if (activeFilters > 1) {
      return NextResponse.json({
        ok: false,
        error: { code: 'VALIDATION_ERROR', message: 'Chỉ được dùng một bộ lọc memberId, eventId hoặc albumId' }
      }, { status: 400 });
    }
    const media = (await resolveTreeForUser(params.treeId, userId)).mediaMetadata;
    if (memberId) return NextResponse.json(media.filter((item) => item.memberIds.includes(memberId)));
    if (eventId) return NextResponse.json(media.filter((item) => item.eventIds.includes(eventId)));
    if (albumId) return NextResponse.json(media.filter((item) => item.albumId === albumId));
    return NextResponse.json(media);
  } catch (error) {
    return eventMediaRouteError(error, 'media');
  }
}
