import { NextResponse } from 'next/server';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { treeRouteError } from '@/lib/services/tree-api-errors';
import { treeService } from '@/lib/services/tree-service';

export const runtime = 'nodejs';

export async function GET(): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    return NextResponse.json(await treeService.listTreesForUser(userId));
  } catch (error) {
    return treeRouteError(error);
  }
}

export async function POST(request: Request): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    const tree = await treeService.createTree(userId, await request.json());
    return NextResponse.json(tree, { status: 201 });
  } catch (error) {
    return treeRouteError(error);
  }
}
