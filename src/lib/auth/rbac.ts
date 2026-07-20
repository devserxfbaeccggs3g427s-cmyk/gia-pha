import type { FamilyTree, FamilyTreeKind, TreeRole } from '@/data/types';
import { getTrees } from '@/lib/blob/readers';

export type TreePermission = 'READ' | 'CREATE' | 'UPDATE' | 'DELETE' | 'ASSIGN_ROLE';

const ROLE_PERMISSIONS: Readonly<Record<TreeRole, ReadonlySet<TreePermission>>> = {
  ADMIN: new Set(['READ', 'CREATE', 'UPDATE', 'DELETE', 'ASSIGN_ROLE']),
  EDITOR: new Set(['READ', 'CREATE', 'UPDATE', 'DELETE']),
  VIEWER: new Set(['READ'])
};

export class AuthorizationError extends Error {
  constructor(
    public readonly code:
      | 'TREE_NOT_FOUND'
      | 'FORBIDDEN'
      | 'NOT_COMPOSITE_TREE'
      | 'SOURCE_NOT_STANDALONE'
      | 'SOURCE_FORBIDDEN'
      | 'SOURCE_SHARING_NOT_CONSENTED',
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

/**
 * Asserts that the actor has ADMIN-level access to the composite tree (owner
 * or ADMIN membership). In MVP only composite owner/Admin may mutate composite
 * configuration (Task 3.3).
 */
export async function requireCompositeAdminPermission(
  treeId: string,
  userId: string
): Promise<{ tree: FamilyTree }> {
  const trees = await getTrees();
  const tree = trees.find((candidate) => candidate.id === treeId);

  if (!tree) {
    throw new AuthorizationError('TREE_NOT_FOUND', 'Family tree not found');
  }

  const effectiveKind: FamilyTreeKind = tree.kind ?? 'STANDALONE';
  if (effectiveKind !== 'COMPOSITE') {
    throw new AuthorizationError('NOT_COMPOSITE_TREE', 'This operation requires a composite tree');
  }

  const role = getUserTreeRole(tree, userId);
  if (role !== 'ADMIN') {
    throw new AuthorizationError(
      'FORBIDDEN',
      'Only the composite tree owner or an Admin may modify composite configuration'
    );
  }

  return { tree };
}

/**
 * Asserts that the actor has READ access to a source tree independently of
 * the composite membership (Task 3.4, Requirement 7.1).
 *
 * Returns the resolved source tree so callers can chain further validation.
 */
export async function requireSourceReadPermission(
  sourceTreeId: string,
  userId: string
): Promise<{ sourceTree: FamilyTree }> {
  const trees = await getTrees();
  const sourceTree = trees.find((candidate) => candidate.id === sourceTreeId);

  if (!sourceTree) {
    throw new AuthorizationError('TREE_NOT_FOUND', `Source tree "${sourceTreeId}" not found`);
  }

  const effectiveKind: FamilyTreeKind = sourceTree.kind ?? 'STANDALONE';
  if (effectiveKind !== 'STANDALONE') {
    throw new AuthorizationError(
      'SOURCE_NOT_STANDALONE',
      'A composite tree cannot be used as a source in MVP'
    );
  }

  const role = getUserTreeRole(sourceTree, userId);
  if (!role || !hasPermission(role, 'READ')) {
    throw new AuthorizationError(
      'SOURCE_FORBIDDEN',
      `You do not have READ permission on source tree "${sourceTreeId}"`
    );
  }

  return { sourceTree };
}

/**
 * Asserts that the actor holds ADMIN permission on the source tree so that
 * they may toggle composite-sharing consent flags
 * (`allowCompositeSharing`, `shareLivingDetails`).
 *
 * Per Requirement 7.5 and the design authorization model, only a source ADMIN
 * is permitted to grant sharing consent. Composite membership alone is
 * insufficient (Task 3.4).
 */
export async function requireSourceAdminConsent(
  sourceTreeId: string,
  userId: string
): Promise<{ sourceTree: FamilyTree }> {
  const trees = await getTrees();
  const sourceTree = trees.find((candidate) => candidate.id === sourceTreeId);

  if (!sourceTree) {
    throw new AuthorizationError('TREE_NOT_FOUND', `Source tree "${sourceTreeId}" not found`);
  }

  const role = getUserTreeRole(sourceTree, userId);
  if (role !== 'ADMIN') {
    throw new AuthorizationError(
      'SOURCE_SHARING_NOT_CONSENTED',
      `Only the source tree owner or an Admin may grant composite-sharing consent for "${sourceTreeId}"`
    );
  }

  return { sourceTree };
}

