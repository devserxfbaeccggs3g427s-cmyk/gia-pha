import { NextResponse } from 'next/server';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { serviceRouteError } from '@/lib/api/service-error-handler';
import { shareLinkService } from '@/lib/services/share-link-service';

export const runtime = 'nodejs';

interface RouteContext {
  params: Promise<{ treeId: string; linkId: string }>;
}

export async function DELETE(_request: Request, { params }: RouteContext): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    const { treeId, linkId } = await params;
    await requireTreePermission(treeId, userId, 'ASSIGN_ROLE');
    await shareLinkService.revokeShareLink(treeId, linkId);
    return NextResponse.json({ ok: true });
  } catch (error) {
    return serviceRouteError(error, 'Không thể thu hồi share link');
  }
}
