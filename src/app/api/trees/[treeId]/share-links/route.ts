import { NextResponse } from 'next/server';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { backupShareRouteError } from '@/lib/services/backup-share-api-errors';
import { shareLinkService } from '@/lib/services/share-link-service';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

interface RouteContext {
  params: { treeId: string };
}

export async function GET(_request: Request, { params }: RouteContext): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    await requireTreePermission(params.treeId, userId, 'ASSIGN_ROLE');
    return NextResponse.json({ shareLinks: await shareLinkService.listShareLinks(params.treeId) }, {
      headers: { 'Cache-Control': 'private, no-store' }
    });
  } catch (error) {
    return backupShareRouteError(error);
  }
}

export async function POST(request: Request, { params }: RouteContext): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    await requireTreePermission(params.treeId, userId, 'ASSIGN_ROLE');
    const link = await shareLinkService.createShareLink(params.treeId, await request.json());
    return NextResponse.json(
      { ...link, url: `${new URL(request.url).origin}/share/${link.token}` },
      { status: 201, headers: { 'Cache-Control': 'private, no-store' } }
    );
  } catch (error) {
    return backupShareRouteError(error);
  }
}
