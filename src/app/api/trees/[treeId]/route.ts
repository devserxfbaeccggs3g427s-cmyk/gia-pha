import { NextResponse } from 'next/server';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { treeRouteError } from '@/lib/services/tree-api-errors';
import { treeService } from '@/lib/services/tree-service';
import { resolveTreeForUser } from '@/lib/services/tree-data-provider';

export const runtime = 'nodejs';

interface RouteContext {
  params: { treeId: string };
}

export async function GET(_request: Request, { params }: RouteContext): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    await requireTreePermission(params.treeId, userId, 'READ');
    return NextResponse.json(await resolveTreeForUser(params.treeId, userId));
  } catch (error) {
    return treeRouteError(error);
  }
}

export async function PUT(request: Request, { params }: RouteContext): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    await requireTreePermission(params.treeId, userId, 'UPDATE');
    return NextResponse.json(await treeService.updateTree(params.treeId, await request.json()));
  } catch (error) {
    return treeRouteError(error);
  }
}

export async function DELETE(_request: Request, { params }: RouteContext): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    await requireTreePermission(params.treeId, userId, 'DELETE');
    await treeService.deleteTree(params.treeId);
    return new NextResponse(null, { status: 204 });
  } catch (error) {
    return treeRouteError(error);
  }
}
