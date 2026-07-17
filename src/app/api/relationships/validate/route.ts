import { NextResponse } from 'next/server';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { relationshipRouteError } from '@/lib/services/relationship-api-errors';
import { relationshipService } from '@/lib/services/relationship-service';

export const runtime = 'nodejs';

export async function POST(request: Request): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    const body = await request.json() as { treeId?: string; data?: unknown; relationship?: unknown } & Record<string, unknown>;
    const treeId = body.treeId ?? new URL(request.url).searchParams.get('treeId') ?? undefined;
    if (!treeId) return NextResponse.json({ ok: false, error: { code: 'VALIDATION_ERROR', message: 'treeId is required' } }, { status: 400 });
    await requireTreePermission(treeId, userId, 'CREATE');
    const input = body.data ?? body.relationship ?? Object.fromEntries(Object.entries(body).filter(([key]) => key !== 'treeId'));
    return NextResponse.json(await relationshipService.validateRelationship(treeId, input));
  } catch (error) {
    return relationshipRouteError(error);
  }
}
