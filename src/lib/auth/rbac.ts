import type { FamilyTree, TreeRole } from '@/data/types';
import { getTrees } from '@/lib/blob/readers';

export type TreePermission = 'READ' | 'CREATE' | 'UPDATE' | 'DELETE' | 'ASSIGN_ROLE';

const ROLE_PERMISSIONS: Readonly<Record<TreeRole, ReadonlySet<TreePermission>>> = {
  ADMIN: new Set(['READ', 'CREATE', 'UPDATE', 'DELETE', 'ASSIGN_ROLE']),
  EDITOR: new Set(['READ', 'CREATE', 'UPDATE', 'DELETE']),
  VIEWER: new Set(['READ'])
};

export class AuthorizationError extends Error {
  constructor(
    public readonly code: 'TREE_NOT_FOUND' | 'FORBIDDEN',
    message: string
  ) {
    super(message);
    this.name = 'AuthorizationError';
  }
}

export function hasPermission(role: TreeRole, permission: TreePermission): boolean {
  return ROLE_PERMISSIONS[role].has(permission);
}

export function getUserTreeRole(tree: FamilyTree, userId: string): TreeRole | null {
  if (tree.ownerId === userId) return 'ADMIN';
  return tree.memberships.find((membership) => membership.userId === userId)?.role ?? null;
}

export function canAccessTree(
  tree: FamilyTree,
  userId: string,
  permission: TreePermission
): boolean {
  const role = getUserTreeRole(tree, userId);
  return role ? hasPermission(role, permission) : false;
}

export async function requireTreePermission(
  treeId: string,
  userId: string,
  permission: TreePermission
): Promise<{ tree: FamilyTree; role: TreeRole }> {
  const trees = await getTrees();
  const tree = trees.find((candidate) => candidate.id === treeId);

  if (!tree) {
    throw new AuthorizationError('TREE_NOT_FOUND', 'Family tree not found');
  }

  const role = getUserTreeRole(tree, userId);
  if (!role || !hasPermission(role, permission)) {
    throw new AuthorizationError('FORBIDDEN', 'You do not have permission to perform this action');
  }

  return { tree, role };
}

