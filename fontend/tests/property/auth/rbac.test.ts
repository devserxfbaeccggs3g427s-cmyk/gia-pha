import fc from 'fast-check';
import { describe, expect, it } from 'vitest';
import type { TreeRole } from '@/data/types';
import { canAccessTree, hasPermission } from '@/lib/auth/rbac';
import { buildFamilyTree } from '../../utils/factories';

describe('Feature: family-genealogy-management, Property 2: Role-Based Permission Enforcement', () => {
  it('never grants a permission outside the role capability set', () => {
    const roles: TreeRole[] = ['ADMIN', 'EDITOR', 'VIEWER'];
    const permissions = ['READ', 'CREATE', 'UPDATE', 'DELETE', 'ASSIGN_ROLE'] as const;

    fc.assert(
      fc.property(fc.constantFrom(...roles), fc.constantFrom(...permissions), (role, permission) => {
        const tree = buildFamilyTree({ ownerId: 'owner_1', memberships: [{ userId: 'member_1', role, createdAt: new Date().toISOString() }] });
        const expected =
          role === 'ADMIN' ||
          (role === 'EDITOR' && permission !== 'ASSIGN_ROLE') ||
          (role === 'VIEWER' && permission === 'READ');

        expect(hasPermission(role, permission)).toBe(expected);
        expect(canAccessTree(tree, 'member_1', permission)).toBe(expected);
      }),
      { numRuns: 100 }
    );
  });

  it('always treats the tree owner as an ADMIN', () => {
    const tree = buildFamilyTree({ ownerId: 'owner_1', memberships: [{ userId: 'owner_1', role: 'VIEWER', createdAt: new Date().toISOString() }] });
    expect(canAccessTree(tree, 'owner_1', 'ASSIGN_ROLE')).toBe(true);
    expect(canAccessTree(tree, 'owner_1', 'DELETE')).toBe(true);
  });
});
