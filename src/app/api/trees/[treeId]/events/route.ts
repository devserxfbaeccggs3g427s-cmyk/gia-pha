import { NextResponse } from 'next/server';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { eventMediaRouteError } from '@/lib/services/event-media-api-errors';
import { eventService } from '@/lib/services/event-service';
import { requireStandaloneMutationTarget } from '@/lib/services/composite-mutation-guard';
import { resolveTreeForUser } from '@/lib/services/tree-data-provider';

export const runtime = 'nodejs';

export async function GET(
  request: Request,
  { params }: { params: { treeId: string } }
): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    await requireTreePermission(params.treeId, userId, 'READ');
    const url = new URL(request.url);
    if (url.searchParams.get('upcoming') === 'true') {
      const rawDays = url.searchParams.get('days');
      const days = rawDays === null ? 7 : Number(rawDays);
      return NextResponse.json(await eventService.getUpcomingEvents(params.treeId, new Date(), days));
    }
    return NextResponse.json((await resolveTreeForUser(params.treeId, userId)).events);
  } catch (error) {
    return eventMediaRouteError(error, 'event');
  }
}

export async function POST(
  request: Request,
  { params }: { params: { treeId: string } }
): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    await requireTreePermission(params.treeId, userId, 'CREATE');
    await requireStandaloneMutationTarget(params.treeId);
    const event = await eventService.createEvent(params.treeId, await request.json(), userId);
    return NextResponse.json(event, { status: 201 });
  } catch (error) {
    return eventMediaRouteError(error, 'event');
  }
}
