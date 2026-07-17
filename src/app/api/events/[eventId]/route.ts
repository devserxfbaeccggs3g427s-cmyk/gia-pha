import { NextResponse } from 'next/server';
import { getEvents, getTrees } from '@/lib/blob/readers';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { eventMediaRouteError } from '@/lib/services/event-media-api-errors';
import { eventService } from '@/lib/services/event-service';

export const runtime = 'nodejs';

interface RouteContext {
  params: { eventId: string };
}

async function findEventTree(request: Request, eventId: string): Promise<string | null> {
  const requestedTreeId = new URL(request.url).searchParams.get('treeId');
  if (requestedTreeId) {
    return (await getEvents(requestedTreeId)).some((event) => event.id === eventId)
      ? requestedTreeId
      : null;
  }
  for (const tree of await getTrees()) {
    if ((await getEvents(tree.id)).some((event) => event.id === eventId)) return tree.id;
  }
  return null;
}

export async function GET(request: Request, { params }: RouteContext): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    const treeId = await findEventTree(request, params.eventId);
    if (!treeId) return notFound();
    await requireTreePermission(treeId, userId, 'READ');
    return NextResponse.json(await eventService.getEventWithRelations(treeId, params.eventId));
  } catch (error) {
    return eventMediaRouteError(error, 'event');
  }
}

export async function PUT(request: Request, { params }: RouteContext): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    const treeId = await findEventTree(request, params.eventId);
    if (!treeId) return notFound();
    await requireTreePermission(treeId, userId, 'UPDATE');
    return NextResponse.json(
      await eventService.updateEvent(treeId, params.eventId, await request.json(), userId)
    );
  } catch (error) {
    return eventMediaRouteError(error, 'event');
  }
}

export async function DELETE(request: Request, { params }: RouteContext): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    const treeId = await findEventTree(request, params.eventId);
    if (!treeId) return notFound();
    await requireTreePermission(treeId, userId, 'DELETE');
    await eventService.deleteEvent(treeId, params.eventId, userId);
    return new NextResponse(null, { status: 204 });
  } catch (error) {
    return eventMediaRouteError(error, 'event');
  }
}

function notFound(): NextResponse {
  return NextResponse.json(
    { ok: false, error: { code: 'NOT_FOUND', message: 'Event not found' } },
    { status: 404 }
  );
}
