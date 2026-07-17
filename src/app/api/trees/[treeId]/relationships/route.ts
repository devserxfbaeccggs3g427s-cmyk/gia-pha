import { NextResponse } from 'next/server';
import { getRelationships } from '@/lib/blob/readers';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { relationshipRouteError } from '@/lib/services/relationship-api-errors';
import { relationshipService } from '@/lib/services/relationship-service';

export const runtime = 'nodejs';

export async function GET(
  request: Request,
  { params }: { params: { treeId: string } }
): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    await requireTreePermission(params.treeId, userId, 'READ');
    const memberId = new URL(request.url).searchParams.get('memberId');
    return NextResponse.json(memberId
      ? await relationshipService.getRelationshipsForMember(params.treeId, memberId)
      : await getRelationships(params.treeId));
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
    const relationship = await relationshipService.createRelationship(params.treeId, await request.json(), userId);
    return NextResponse.json(relationship, { status: 201 });
  } catch (error) {
    return relationshipRouteError(error);
  }
}
