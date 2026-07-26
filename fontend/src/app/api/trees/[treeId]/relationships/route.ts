import { NextResponse } from 'next/server';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { serviceRouteError } from '@/lib/api/service-error-handler';
import { relationshipService } from '@/lib/services/relationship-service';
import { springFetch } from '@/lib/api/spring-client';
import { requireBridgeCookie } from '@/lib/api/spring-guard';
import {
    fromSpringRelationship,
    type SpringRelationshipDto
} from '@/lib/api/dto-mapper';

export const runtime = 'nodejs';

export async function GET(
  request: Request,
  { params }: { params: Promise<{ treeId: string }> }
): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    const { treeId } = await params;
    await requireTreePermission(treeId, userId, 'READ');
    const memberId = new URL(request.url).searchParams.get('memberId');
    if (memberId) {
      return NextResponse.json(
        await relationshipService.getRelationshipsForMember(treeId, memberId)
      );
    }
    const cookie = await requireBridgeCookie();
    const res = await springFetch<SpringRelationshipDto[] | { data?: SpringRelationshipDto[] }>(
      `/api/trees/${encodeURIComponent(treeId)}/relationships`,
      { method: 'GET', public: true },
      cookie
    );
    const list = Array.isArray(res.body) ? res.body : res.body?.data ?? [];
    return NextResponse.json(list.map(fromSpringRelationship));
  } catch (error) {
    return serviceRouteError(error, 'Không thể truy xuất quan hệ');
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
    const relationship = await relationshipService.createRelationship(
      treeId,
      await request.json(),
      userId
    );
    return NextResponse.json(relationship, { status: 201 });
  } catch (error) {
    return serviceRouteError(error, 'Không thể tạo quan hệ');
  }
}
