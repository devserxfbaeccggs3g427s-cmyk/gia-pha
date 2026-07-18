import { NextResponse } from 'next/server';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { backupShareRouteError } from '@/lib/services/backup-share-api-errors';
import { shareLinkService } from '@/lib/services/share-link-service';

export const runtime = 'nodejs';

interface RouteContext {
  params: { treeId: string; linkId: string };
}

export async function DELETE(_request: Request, { params }: RouteContext): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    await requireTreePermission(params.treeId, userId, 'ASSIGN_ROLE');
    await shareLinkService.revokeShareLink(params.treeId, params.linkId);
    return new NextResponse(null, { status: 204 });
  } catch (error) {
    return backupShareRouteError(error);
  }
}
