import { NextResponse } from 'next/server';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { serviceRouteError } from '@/lib/api/service-error-handler';
import { mediaService } from '@/lib/services/media-service';

export const runtime = 'nodejs';

export async function GET(
  request: Request,
  { params }: { params: Promise<{ treeId: string }> }
): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    const { treeId } = await params;
    await requireTreePermission(treeId, userId, 'READ');
    const query = new URL(request.url).searchParams;
    const memberId = query.get('memberId') ?? undefined;
    const eventId = query.get('eventId') ?? undefined;
    const albumId = query.get('albumId') ?? undefined;
    const activeFilters = [memberId, eventId, albumId].filter(Boolean).length;
    if (activeFilters > 1) {
      return NextResponse.json(
        { ok: false, error: { code: 'INVALID_INPUT', message: 'Chỉ được truy xuất media theo một tiêu chí' } },
        { status: 400 }
      );
    }
    return NextResponse.json(
      await mediaService.listMedia(treeId, { memberId, eventId, albumId })
    );
  } catch (error) {
    return serviceRouteError(error, 'Không thể truy xuất media');
  }
}
