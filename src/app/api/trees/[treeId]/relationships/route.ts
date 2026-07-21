import { NextResponse } from 'next/server';
import { getRelationships, getTrees } from '@/lib/blob/readers';
import { compositeConfigService } from '@/lib/services/composite-config-service';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { relationshipRouteError } from '@/lib/services/relationship-api-errors';
import { relationshipService } from '@/lib/services/relationship-service';
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
    const memberId = new URL(request.url).searchParams.get('memberId');
    const relationships = (await resolveTreeForUser(params.treeId, userId)).relationships;
    return NextResponse.json(memberId ? relationships.filter((item) => item.sourceMemberId === memberId || item.targetMemberId === memberId) : relationships);
  } catch (error) {
    return relationshipRouteError(error);
  }
}

export async function POST(
  request: Request,
  { params }: { params: { treeId: string } }
): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    await requireTreePermission(params.treeId, userId, 'CREATE');
    const input = await request.json() as { sourceMemberId: string; targetMemberId: string; type: string; customType?: string; marriageDate?: string; divorceDate?: string; marriageStatus?: string };
    const tree = (await getTrees()).find((item) => item.id === params.treeId);
    if (tree?.kind === 'COMPOSITE') {
      const resolved = await resolveTreeForUser(params.treeId, userId);
      const source = resolved.members.find((member) => member.id === input.sourceMemberId);
      const target = resolved.members.find((member) => member.id === input.targetMemberId);
      if (!source || !target) return NextResponse.json({ ok: false, error: { code: 'NOT_FOUND', message: 'Resolved member not found' } }, { status: 404 });
      const sourceReference = source.preferredReference ?? source.sourceReferences[0];
      const targetReference = target.preferredReference ?? target.sourceReferences[0];
      if (!sourceReference || !targetReference) return NextResponse.json({ ok: false, error: { code: 'INVALID_COMPOSITE_CONFIG', message: 'Source reference not found' } }, { status: 422 });
      const config = await compositeConfigService.getConfig(params.treeId);
      const updated = await compositeConfigService.createCrossTreeRelationship(params.treeId, userId, { source: sourceReference, target: targetReference, type: input.type, customType: input.customType, marriageDate: input.marriageDate, divorceDate: input.divorceDate, marriageStatus: input.marriageStatus }, config.revision);
      return NextResponse.json(updated.crossTreeRelationships[updated.crossTreeRelationships.length - 1], { status: 201 });
    }
    await requireStandaloneMutationTarget(params.treeId);
    const relationship = await relationshipService.createRelationship(params.treeId, input, userId);
    return NextResponse.json(relationship, { status: 201 });
  } catch (error) {
    return relationshipRouteError(error);
  }
}
