import { NextResponse } from 'next/server';
import { getMembers, getTrees } from '@/lib/blob/readers';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { memberService } from '@/lib/services/member-service';
import { memberRouteError } from '@/lib/services/member-api-errors';
import { requireStandaloneMutationTarget } from '@/lib/services/composite-mutation-guard';
import { resolveTreeForUser } from '@/lib/services/tree-data-provider';

export const runtime = 'nodejs';

interface RouteContext {
  params: { memberId: string };
}

async function findMemberTree(request: Request, memberId: string): Promise<string | null> {
  const requestedTreeId = new URL(request.url).searchParams.get('treeId');
  if (requestedTreeId) {
    const member = (await getMembers(requestedTreeId)).find((candidate) => candidate.id === memberId);
    return member ? requestedTreeId : null;
  }
  const trees = await getTrees();
  for (const tree of trees) {
    if ((await getMembers(tree.id)).some((member) => member.id === memberId)) return tree.id;
  }
  return null;
}

export async function GET(request: Request, { params }: RouteContext): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    const requestedTreeId = new URL(request.url).searchParams.get('treeId');
    const treeId = requestedTreeId ?? await findMemberTree(request, params.memberId);
    if (!treeId) return NextResponse.json({ ok: false, error: { code: 'NOT_FOUND', message: 'Member not found' } }, { status: 404 });
    await requireTreePermission(treeId, userId, 'READ');
    const resolved = await resolveTreeForUser(treeId, userId);
    const member = resolved.members.find((item) => item.id === params.memberId);
    if (!member) return NextResponse.json({ ok: false, error: { code: 'NOT_FOUND', message: 'Member not found' } }, { status: 404 });
    return NextResponse.json({ ...member, member, relationships: resolved.relationships.filter((item) => item.sourceMemberId === member.id || item.targetMemberId === member.id), relatedMembers: resolved.members.filter((item) => resolved.relationships.some((rel) => (rel.sourceMemberId === member.id && rel.targetMemberId === item.id) || (rel.targetMemberId === member.id && rel.sourceMemberId === item.id))), events: resolved.events.filter((item) => item.memberIds.includes(member.id)), media: resolved.mediaMetadata.filter((item) => item.memberIds.includes(member.id)), status: member.isAlive ? 'ALIVE' : 'DECEASED', lifespan: null });
  } catch (error) {
    return memberRouteError(error);
  }
}

export async function PUT(request: Request, { params }: RouteContext): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    const treeId = await findMemberTree(request, params.memberId);
    if (!treeId) return NextResponse.json({ ok: false, error: { code: 'NOT_FOUND', message: 'Member not found' } }, { status: 404 });
    await requireTreePermission(treeId, userId, 'UPDATE');
    await requireStandaloneMutationTarget(treeId);
    return NextResponse.json(await memberService.updateMember(treeId, params.memberId, await request.json(), userId));
  } catch (error) {
    return memberRouteError(error);
  }
}

export async function DELETE(request: Request, { params }: RouteContext): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    const treeId = await findMemberTree(request, params.memberId);
    if (!treeId) return NextResponse.json({ ok: false, error: { code: 'NOT_FOUND', message: 'Member not found' } }, { status: 404 });
    await requireTreePermission(treeId, userId, 'DELETE');
    await requireStandaloneMutationTarget(treeId);
    // The client can request this preview before showing its confirmation
    // dialog.  No data is mutated in preview mode.
    if (new URL(request.url).searchParams.get('preview') === 'true') {
      const details = await memberService.getMemberWithRelations(treeId, params.memberId);
      return NextResponse.json({
        member: details.member,
        affectedRelationships: details.relationships,
        affectedEvents: details.events,
        affectedMedia: details.media
      });
    }
    return NextResponse.json(await memberService.deleteMember(treeId, params.memberId, userId));
  } catch (error) {
    return memberRouteError(error);
  }
}
