import { NextResponse } from 'next/server';
import { getRelationships, getTrees } from '@/lib/blob/readers';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { relationshipRouteError } from '@/lib/services/relationship-api-errors';
import { relationshipService } from '@/lib/services/relationship-service';

export const runtime = 'nodejs';

async function findRelationshipTree(request: Request, relationshipId: string): Promise<string | null> {
  const requestedTreeId = new URL(request.url).searchParams.get('treeId');
  if (requestedTreeId) {
    return (await getRelationships(requestedTreeId)).some((relationship) => relationship.id === relationshipId)
      ? requestedTreeId
      : null;
  }
  for (const tree of await getTrees()) {
    if ((await getRelationships(tree.id)).some((relationship) => relationship.id === relationshipId)) return tree.id;
  }
  return null;
}

export async function DELETE(
  request: Request,
  { params }: { params: { relationshipId: string } }
): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    const treeId = await findRelationshipTree(request, params.relationshipId);
    if (!treeId) return NextResponse.json({ ok: false, error: { code: 'NOT_FOUND', message: 'Relationship not found' } }, { status: 404 });
    await requireTreePermission(treeId, userId, 'DELETE');
    await relationshipService.deleteRelationship(treeId, params.relationshipId, userId);
    return new NextResponse(null, { status: 204 });
  } catch (error) {
    return relationshipRouteError(error);
  }
}
