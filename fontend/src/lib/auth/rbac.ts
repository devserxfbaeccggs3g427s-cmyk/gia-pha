import 'server-only';

import type { FamilyTree, TreeRole } from '@/data/types';
import { requireBridgeCookie } from '@/lib/api/spring-guard';
import { springFetch } from '@/lib/api/spring-client';
import {
    fromSpringTree,
    fromSpringTreeMembership,
    type SpringTreeDto,
    type SpringTreeMembershipDto
} from '@/lib/api/dto-mapper';

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

/**
 * Resolve the tree + the requesting user's role by calling the
 * gateway. The microservice exposes tree membership in the
 * `tree-service` endpoint and the {@code tree-memberships} endpoint
 * (per ADR-101 strategy B).
 */
export async function requireTreePermission(
    treeId: string,
    userId: string,
    permission: TreePermission
): Promise<{ tree: FamilyTree; role: TreeRole }> {
    const cookie = await requireBridgeCookie();
    let treeRes;
    try {
        treeRes = await springFetch<SpringTreeDto>(
            `/api/trees/${encodeURIComponent(treeId)}`,
            { method: 'GET', public: true },
            cookie
        );
    } catch (error) {
        if (error instanceof Error && error.message.includes('NOT_FOUND')) {
            throw new AuthorizationError('TREE_NOT_FOUND', 'Family tree not found');
        }
        throw error;
    }

    const tree = fromSpringTree(treeRes.body);

    let memberships: FamilyTree['memberships'] = [];
    try {
        const memRes = await springFetch<SpringTreeMembershipDto[]>(
            `/api/trees/${encodeURIComponent(treeId)}/tree-memberships`,
            { method: 'GET', public: true },
            cookie
        );
        memberships = memRes.body.map(fromSpringTreeMembership);
    } catch {
        memberships = [];
    }
    tree.memberships = memberships;

    const role = getUserTreeRole(tree, userId);
    if (!role) {
        throw new AuthorizationError('FORBIDDEN', 'You do not have access to this tree');
    }
    if (!hasPermission(role, permission)) {
        throw new AuthorizationError('FORBIDDEN', `Role ${role} cannot ${permission}`);
    }
    return { tree, role };
}
