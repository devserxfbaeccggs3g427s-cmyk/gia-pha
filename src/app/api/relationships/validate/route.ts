import { NextResponse } from 'next/server';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { relationshipRouteError } from '@/lib/services/relationship-api-errors';
import { relationshipService } from '@/lib/services/relationship-service';
import { getTrees } from '@/lib/blob/readers';
import { resolveTreeForUser } from '@/lib/services/tree-data-provider';

export const runtime = 'nodejs';

export async function POST(request: Request): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    const body = await request.json() as { treeId?: string; data?: unknown; relationship?: unknown } & Record<string, unknown>;
    const treeId = body.treeId ?? new URL(request.url).searchParams.get('treeId') ?? undefined;
    if (!treeId) return NextResponse.json({ ok: false, error: { code: 'VALIDATION_ERROR', message: 'treeId is required' } }, { status: 400 });
    await requireTreePermission(treeId, userId, 'CREATE');
    const input = body.data ?? body.relationship ?? Object.fromEntries(Object.entries(body).filter(([key]) => key !== 'treeId'));
    const tree = (await getTrees()).find((item) => item.id === treeId);
    if (tree?.kind === 'COMPOSITE') {
      const candidate = input as { sourceMemberId?: string; targetMemberId?: string };
      const resolved = await resolveTreeForUser(treeId, userId);
      const memberIds = new Set(resolved.members.map((member) => member.id));
      const errors: string[] = [];
      if (!candidate.sourceMemberId || !memberIds.has(candidate.sourceMemberId)) errors.push(`Source member "${candidate.sourceMemberId ?? ''}" was not found`);
      if (!candidate.targetMemberId || !memberIds.has(candidate.targetMemberId)) errors.push(`Target member "${candidate.targetMemberId ?? ''}" was not found`);
      if (candidate.sourceMemberId === candidate.targetMemberId) errors.push('A member cannot be related to itself');
      return NextResponse.json({ valid: errors.length === 0, errors });
    }
    return NextResponse.json(await relationshipService.validateRelationship(treeId, input));
  } catch (error) {
    return relationshipRouteError(error);
  }
}
