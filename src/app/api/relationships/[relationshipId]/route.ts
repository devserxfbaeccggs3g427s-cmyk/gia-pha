import { NextResponse } from 'next/server';
import { getRelationships, getTrees } from '@/lib/blob/readers';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { relationshipRouteError } from '@/lib/services/relationship-api-errors';
import { relationshipService } from '@/lib/services/relationship-service';
import { requireStandaloneMutationTarget } from '@/lib/services/composite-mutation-guard';
import { compositeConfigService } from '@/lib/services/composite-config-service';
import { resolveTreeForUser } from '@/lib/services/tree-data-provider';

export const runtime = 'nodejs';

async function findRelationshipTree(request: Request, relationshipId: string): Promise<string | null> {
  const requestedTreeId = new URL(request.url).searchParams.get('treeId');
  if (requestedTreeId) {
    return (await getRelationships(requestedTreeId)).some((relationship) => relationship.id === relationshipId)
      ? requestedTreeId
      : null;
  }
  for (const tree of await getTrees()) {
    if ((await getRelationships(tree.id)).some((relationship) => relationship.id === relationshipId)) return tree.id;
  }
  return null;
}

export async function DELETE(
  request: Request,
  { params }: { params: { relationshipId: string } }
): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    const requestedTreeId = new URL(request.url).searchParams.get('treeId');
    if (requestedTreeId) {
      const tree = (await getTrees()).find((item) => item.id === requestedTreeId);
      if (tree?.kind === 'COMPOSITE') {
        await requireTreePermission(requestedTreeId, userId, 'DELETE');
        const config = await compositeConfigService.getConfig(requestedTreeId);
        let configRelationshipId = params.relationshipId;
        if (!config.crossTreeRelationships.some((relationship) => relationship.id === configRelationshipId)) {
          const resolved = await resolveTreeForUser(requestedTreeId, userId);
          const virtualRelationship = resolved.relationships.find((relationship) => relationship.id === params.relationshipId);
          if (virtualRelationship && !virtualRelationship.isCrossTree) {
            const sourceTreeId = virtualRelationship.provenance.find((entry) => entry.entityType === 'RELATIONSHIP')?.treeId;
            return NextResponse.json({ ok: false, error: { code: 'COMPOSITE_READ_ONLY', message: 'Đây là mối quan hệ thuộc cây gia phả nguồn nên không thể xoá trực tiếp trong gia phả tổng hợp. Vui lòng mở cây nguồn để chỉnh sửa hoặc xoá mối quan hệ này.', details: { sourceTreeId } } }, { status: 422 });
          }
          const provenanceId = virtualRelationship?.provenance.find((entry) => entry.treeId === requestedTreeId && entry.entityType === 'RELATIONSHIP')?.entityId;
          if (!provenanceId || !config.crossTreeRelationships.some((relationship) => relationship.id === provenanceId)) return NextResponse.json({ ok: false, error: { code: 'NOT_FOUND', message: 'Không tìm thấy mối quan hệ trong gia phả tổng hợp. Hãy tải lại trang và thử lại.' } }, { status: 404 });
          configRelationshipId = provenanceId;
        }
        await compositeConfigService.deleteCrossTreeRelationship(requestedTreeId, userId, configRelationshipId, config.revision);
        return new NextResponse(null, { status: 204 });
      }
    }
    const treeId = await findRelationshipTree(request, params.relationshipId);
    if (!treeId) return NextResponse.json({ ok: false, error: { code: 'NOT_FOUND', message: 'Relationship not found' } }, { status: 404 });
    await requireTreePermission(treeId, userId, 'DELETE');
    await requireStandaloneMutationTarget(treeId);
    await relationshipService.deleteRelationship(treeId, params.relationshipId, userId);
    return new NextResponse(null, { status: 204 });
  } catch (error) {
    return relationshipRouteError(error);
  }
}
