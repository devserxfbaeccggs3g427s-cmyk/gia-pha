import { NextResponse } from 'next/server';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { serviceRouteError } from '@/lib/api/service-error-handler';
import { relationshipService } from '@/lib/services/relationship-service';

export const runtime = 'nodejs';

interface RouteContext {
  params: Promise<{ relationshipId: string }>;
}

export async function DELETE(request: Request, { params }: RouteContext): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    const { relationshipId } = await params;
    const treeId = new URL(request.url).searchParams.get('treeId');
    if (!treeId) {
      return NextResponse.json(
        { ok: false, error: { code: 'INVALID_INPUT', message: 'treeId query parameter is required' } },
        { status: 400 }
      );
    }
    await requireTreePermission(treeId, userId, 'DELETE');
    await relationshipService.deleteRelationship(treeId, relationshipId, userId);
    return new NextResponse(null, { status: 204 });
  } catch (error) {
    return serviceRouteError(error, 'Không thể xóa quan hệ');
  }
}
