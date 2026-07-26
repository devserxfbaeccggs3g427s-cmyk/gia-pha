import { NextResponse } from 'next/server';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { serviceRouteError } from '@/lib/api/service-error-handler';
import { eventService } from '@/lib/services/event-service';

export const runtime = 'nodejs';

export async function GET(
  request: Request,
  { params }: { params: Promise<{ treeId: string }> }
): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    const { treeId } = await params;
    await requireTreePermission(treeId, userId, 'READ');
    const url = new URL(request.url);
    if (url.searchParams.get('upcoming') === 'true') {
      const rawDays = url.searchParams.get('days');
      const days = rawDays === null ? 7 : Number(rawDays);
      return NextResponse.json(
        await eventService.getUpcomingEvents(treeId, {
          now: new Date(),
          limit: Number.isFinite(days) && days > 0 ? Math.min(days, 30) : 7
        })
      );
    }
    return NextResponse.json(await eventService.getEventsForTree(treeId));
  } catch (error) {
    return serviceRouteError(error, 'Không thể truy xuất sự kiện');
  }
}

export async function POST(
  request: Request,
  { params }: { params: Promise<{ treeId: string }> }
): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    const { treeId } = await params;
    await requireTreePermission(treeId, userId, 'CREATE');
    const event = await eventService.createEvent(treeId, await request.json(), userId);
    return NextResponse.json(event, { status: 201 });
  } catch (error) {
    return serviceRouteError(error, 'Không thể tạo sự kiện');
  }
}
