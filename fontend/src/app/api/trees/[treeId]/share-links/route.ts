import { NextResponse } from 'next/server';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { serviceRouteError } from '@/lib/api/service-error-handler';
import { shareLinkService } from '@/lib/services/share-link-service';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

interface RouteContext {
  params: Promise<{ treeId: string }>;
}

export async function GET(_request: Request, { params }: RouteContext): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    const { treeId } = await params;
    await requireTreePermission(treeId, userId, 'ASSIGN_ROLE');
    return NextResponse.json(
      { shareLinks: await shareLinkService.listShareLinks(treeId) },
      { headers: { 'Cache-Control': 'private, no-store' } }
    );
  } catch (error) {
    return serviceRouteError(error, 'Không thể truy xuất share link');
  }
}

export async function POST(request: Request, { params }: RouteContext): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    const { treeId } = await params;
    await requireTreePermission(treeId, userId, 'ASSIGN_ROLE');
    const body = (await request.json()) ?? {};
    const link = await shareLinkService.createShareLink(treeId, {
      permission: body.permission,
      expiresAt: body.expiresAt,
      createdBy: userId
    });
    return NextResponse.json(link, { status: 201 });
  } catch (error) {
    return serviceRouteError(error, 'Không thể tạo share link');
  }
}
