import { NextResponse } from 'next/server';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { serviceRouteError } from '@/lib/api/service-error-handler';
import { memberService } from '@/lib/services/member-service';

export const runtime = 'nodejs';

interface RouteContext {
  params: Promise<{ memberId: string }>;
}

export async function GET(request: Request, { params }: RouteContext): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    const { memberId } = await params;
    const treeId = new URL(request.url).searchParams.get('treeId');
    if (!treeId) {
      return NextResponse.json(
        { ok: false, error: { code: 'INVALID_INPUT', message: 'treeId query parameter is required' } },
        { status: 400 }
      );
    }
    await requireTreePermission(treeId, userId, 'READ');
    return NextResponse.json(await memberService.getMemberWithRelations(treeId, memberId));
  } catch (error) {
    return serviceRouteError(error, 'Không thể truy xuất thành viên');
  }
}

export async function PATCH(request: Request, { params }: RouteContext): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    const { memberId } = await params;
    const treeId = new URL(request.url).searchParams.get('treeId');
    if (!treeId) {
      return NextResponse.json(
        { ok: false, error: { code: 'INVALID_INPUT', message: 'treeId query parameter is required' } },
        { status: 400 }
      );
    }
    await requireTreePermission(treeId, userId, 'UPDATE');
    const member = await memberService.updateMember(
      treeId,
      memberId,
      await request.json(),
      userId
    );
    return NextResponse.json(member);
  } catch (error) {
    return serviceRouteError(error, 'Không thể cập nhật thành viên');
  }
}

export async function DELETE(request: Request, { params }: RouteContext): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    const { memberId } = await params;
    const treeId = new URL(request.url).searchParams.get('treeId');
    if (!treeId) {
      return NextResponse.json(
        { ok: false, error: { code: 'INVALID_INPUT', message: 'treeId query parameter is required' } },
        { status: 400 }
      );
    }
    await requireTreePermission(treeId, userId, 'DELETE');
    const result = await memberService.deleteMember(treeId, memberId, userId);
    return NextResponse.json(result);
  } catch (error) {
    return serviceRouteError(error, 'Không thể xóa thành viên');
  }
}
