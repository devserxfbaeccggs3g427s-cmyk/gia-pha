import { NextResponse } from 'next/server';
import { ZodError } from 'zod';
import { roleAssignmentSchema } from '@/data/schemas';
import { getTrees, getUsers } from '@/lib/blob/readers';
import { putTrees } from '@/lib/blob/writers';
import { AuthenticationError, requireAuthenticatedUserId } from '@/lib/auth/guards';
import { AuthorizationError, requireTreePermission } from '@/lib/auth/rbac';

interface RouteContext {
  params: { treeId: string; userId: string };
}

export async function PATCH(request: Request, { params }: RouteContext): Promise<NextResponse> {
  try {
    const actingUserId = await requireAuthenticatedUserId();
    const input = roleAssignmentSchema.parse(await request.json());
    const { tree } = await requireTreePermission(params.treeId, actingUserId, 'ASSIGN_ROLE');

    if (tree.ownerId === params.userId && input.role !== 'ADMIN') {
      return NextResponse.json(
        {
          ok: false,
          error: { code: 'OWNER_ROLE_IMMUTABLE', message: 'The tree owner must remain an ADMIN' }
        },
        { status: 409 }
      );
    }

    const users = await getUsers();
    if (!users.some((user) => user.id === params.userId)) {
      return NextResponse.json(
        { ok: false, error: { code: 'USER_NOT_FOUND', message: 'User not found' } },
        { status: 404 }
      );
    }

    const trees = await getTrees();
    const treeIndex = trees.findIndex((candidate) => candidate.id === params.treeId);
    if (treeIndex === -1) throw new AuthorizationError('TREE_NOT_FOUND', 'Family tree not found');

    const membershipIndex = tree.memberships.findIndex(
      (membership) => membership.userId === params.userId
    );
    const now = new Date().toISOString();
    const memberships = [...tree.memberships];

    if (membershipIndex === -1) {
      memberships.push({ userId: params.userId, role: input.role, createdAt: now });
    } else {
      memberships[membershipIndex] = { ...memberships[membershipIndex], role: input.role };
    }

    const updatedTree = { ...tree, memberships, updatedAt: now };
    trees[treeIndex] = updatedTree;
    await putTrees(trees);

    return NextResponse.json({
      ok: true,
      data: memberships.find((membership) => membership.userId === params.userId)
    });
  } catch (error) {
    if (error instanceof AuthenticationError) {
      return NextResponse.json(
        { ok: false, error: { code: 'UNAUTHENTICATED', message: error.message } },
        { status: 401 }
      );
    }

    if (error instanceof AuthorizationError) {
      return NextResponse.json(
        { ok: false, error: { code: error.code, message: error.message } },
        { status: error.code === 'TREE_NOT_FOUND' ? 404 : 403 }
      );
    }

    if (error instanceof ZodError) {
      return NextResponse.json(
        {
          ok: false,
          error: { code: 'VALIDATION_ERROR', message: 'Invalid role', details: error.flatten() }
        },
        { status: 400 }
      );
    }

    console.error('[auth] Role assignment failed', error);
    return NextResponse.json(
      { ok: false, error: { code: 'INTERNAL_ERROR', message: 'Unable to assign role' } },
      { status: 500 }
    );
  }
}

