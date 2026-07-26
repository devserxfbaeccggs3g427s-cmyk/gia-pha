import { NextResponse } from 'next/server';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { serviceRouteError } from '@/lib/api/service-error-handler';
import { relationshipService } from '@/lib/services/relationship-service';

export const runtime = 'nodejs';

export async function POST(
  request: Request,
  { params }: { params: Promise<Record<string, never>> }
): Promise<NextResponse> {
  try {
    await params;
    const userId = await requireAuthenticatedUserId();
    const body = (await request.json()) as { treeId?: unknown; data?: unknown };
    const treeId = typeof body.treeId === 'string' ? body.treeId : null;
    if (!treeId) {
      return NextResponse.json(
        { ok: false, error: { code: 'INVALID_INPUT', message: 'treeId is required' } },
        { status: 400 }
      );
    }
    await requireTreePermission(treeId, userId, 'CREATE');
    const result = await relationshipService.validateRelationship(
      treeId,
      body.data as Parameters<typeof relationshipService.validateRelationship>[1]
    );
    return NextResponse.json(result, { status: result.valid ? 200 : 422 });
  } catch (error) {
    return serviceRouteError(error, 'Không thể xác thực quan hệ');
  }
}
