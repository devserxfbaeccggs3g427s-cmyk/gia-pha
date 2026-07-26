import { NextResponse } from 'next/server';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { serviceRouteError } from '@/lib/api/service-error-handler';
import { eventService } from '@/lib/services/event-service';

export const runtime = 'nodejs';

interface RouteContext {
  params: Promise<{ eventId: string }>;
}

export async function GET(request: Request, { params }: RouteContext): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    const { eventId } = await params;
    const treeId = new URL(request.url).searchParams.get('treeId');
    if (!treeId) {
      return NextResponse.json(
        { ok: false, error: { code: 'INVALID_INPUT', message: 'treeId query parameter is required' } },
        { status: 400 }
      );
    }
    await requireTreePermission(treeId, userId, 'READ');
    return NextResponse.json(await eventService.getEventWithRelations(treeId, eventId));
  } catch (error) {
    return serviceRouteError(error, 'Không thể truy xuất sự kiện');
  }
}

export async function PATCH(request: Request, { params }: RouteContext): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    const { eventId } = await params;
    const treeId = new URL(request.url).searchParams.get('treeId');
    if (!treeId) {
      return NextResponse.json(
        { ok: false, error: { code: 'INVALID_INPUT', message: 'treeId query parameter is required' } },
        { status: 400 }
      );
    }
    await requireTreePermission(treeId, userId, 'UPDATE');
    const event = await eventService.updateEvent(
      treeId,
      eventId,
      await request.json(),
      userId
    );
    return NextResponse.json(event);
  } catch (error) {
    return serviceRouteError(error, 'Không thể cập nhật sự kiện');
  }
}

export async function DELETE(request: Request, { params }: RouteContext): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    const { eventId } = await params;
    const treeId = new URL(request.url).searchParams.get('treeId');
    if (!treeId) {
      return NextResponse.json(
        { ok: false, error: { code: 'INVALID_INPUT', message: 'treeId query parameter is required' } },
        { status: 400 }
      );
    }
    await requireTreePermission(treeId, userId, 'DELETE');
    await eventService.deleteEvent(treeId, eventId, userId);
    return new NextResponse(null, { status: 204 });
  } catch (error) {
    return serviceRouteError(error, 'Không thể xóa sự kiện');
  }
}
