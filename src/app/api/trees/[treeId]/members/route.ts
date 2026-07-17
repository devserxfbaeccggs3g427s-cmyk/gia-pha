import { NextResponse } from 'next/server';
import { getMembers } from '@/lib/blob/readers';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { memberService } from '@/lib/services/member-service';
import { memberRouteError } from '@/lib/services/member-api-errors';

export const runtime = 'nodejs';

export async function GET(
  _request: Request,
  { params }: { params: { treeId: string } }
): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    await requireTreePermission(params.treeId, userId, 'READ');
    return NextResponse.json(await getMembers(params.treeId));
  } catch (error) {
    return memberRouteError(error);
  }
}

export async function POST(
  request: Request,
  { params }: { params: { treeId: string } }
): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    await requireTreePermission(params.treeId, userId, 'CREATE');
    const member = await memberService.createMember(params.treeId, await request.json(), userId);
    return NextResponse.json(member, { status: 201 });
  } catch (error) {
    return memberRouteError(error);
  }
}
