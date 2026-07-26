import { NextResponse } from 'next/server';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { treeRouteError } from '@/lib/services/tree-api-errors';
import { treeService } from '@/lib/services/tree-service';

export const runtime = 'nodejs';

interface RouteContext {
  params: Promise<{ treeId: string }>;
}

export async function GET(_request: Request, { params }: RouteContext): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    const { treeId } = await params;
    await requireTreePermission(treeId, userId, 'READ');
    return NextResponse.json(await treeService.getTreeWithMembers(treeId));
  } catch (error) {
    return treeRouteError(error);
  }
}

export async function PUT(request: Request, { params }: RouteContext): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    const { treeId } = await params;
    await requireTreePermission(treeId, userId, 'UPDATE');
    return NextResponse.json(await treeService.updateTree(treeId, await request.json()));
  } catch (error) {
    return treeRouteError(error);
  }
}

export async function DELETE(_request: Request, { params }: RouteContext): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    const { treeId } = await params;
    await requireTreePermission(treeId, userId, 'DELETE');
    await treeService.deleteTree(treeId);
    return new NextResponse(null, { status: 204 });
  } catch (error) {
    return treeRouteError(error);
  }
}
