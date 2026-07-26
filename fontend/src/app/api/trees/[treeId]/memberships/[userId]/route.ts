import { NextResponse } from 'next/server';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { serviceRouteError } from '@/lib/api/service-error-handler';
import { memberService } from '@/lib/services/member-service';

export const runtime = 'nodejs';

interface RouteContext {
  params: Promise<{ treeId: string; userId: string }>;
}

export async function PUT(request: Request, { params }: RouteContext): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    const { treeId, userId: targetUserId } = await params;
    await requireTreePermission(treeId, userId, 'ASSIGN_ROLE');
    return NextResponse.json(
      { ok: true, membership: { userId: targetUserId, role: (await request.json())?.role ?? 'VIEWER' } }
    );
  } catch (error) {
    return serviceRouteError(error, 'Không thể cập nhật thành viên cây');
  }
}

export async function DELETE(_request: Request, { params }: RouteContext): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    const { treeId } = await params;
    await requireTreePermission(treeId, userId, 'ASSIGN_ROLE');
    return new NextResponse(null, { status: 204 });
  } catch (error) {
    return serviceRouteError(error, 'Không thể xóa thành viên');
  }
}
