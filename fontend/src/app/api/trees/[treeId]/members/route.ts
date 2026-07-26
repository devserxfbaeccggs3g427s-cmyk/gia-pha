import { NextResponse } from 'next/server';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { serviceRouteError } from '@/lib/api/service-error-handler';
import { memberService } from '@/lib/services/member-service';

export const runtime = 'nodejs';

export async function GET(
  _request: Request,
  { params }: { params: Promise<{ treeId: string }> }
): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    const { treeId } = await params;
    await requireTreePermission(treeId, userId, 'READ');
    return NextResponse.json(await memberService.listMembers(treeId));
  } catch (error) {
    return serviceRouteError(error, 'Không thể truy xuất danh sách thành viên');
  }
}

export async function POST(
  request: Request,
  { params }: { params: Promise<{ treeId: string }> }
): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    const { treeId } = await params;
    await requireTreePermission(treeId, userId, 'CREATE');
    const member = await memberService.createMember(treeId, await request.json(), userId);
    return NextResponse.json(member, { status: 201 });
  } catch (error) {
    return serviceRouteError(error, 'Không thể tạo thành viên');
  }
}
