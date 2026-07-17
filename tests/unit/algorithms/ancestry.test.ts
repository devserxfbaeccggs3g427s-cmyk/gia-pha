import { describe, expect, it } from 'vitest';
import { getAncestryPath } from '@/lib/algorithms/ancestry';
import { buildMember, buildRelationship } from '../../utils/factories';

describe('getAncestryPath', () => {
  it('returns the shortest deterministic path from a root to the target', () => {
    const members = ['root-a', 'root-b', 'parent', 'target'].map((id) => buildMember({ id }));
    const relationships = [
      buildRelationship({ sourceMemberId: 'root-a', targetMemberId: 'parent' }),
      buildRelationship({ sourceMemberId: 'root-b', targetMemberId: 'target' }),
      buildRelationship({ sourceMemberId: 'parent', targetMemberId: 'target' })
    ];

    expect(getAncestryPath(members, relationships, 'target').map((member) => member.id)).toEqual([
      'root-b',
      'target'
    ]);
  });

  it('handles reciprocal records persisted by RelationshipService', () => {
    const members = ['root', 'parent', 'target'].map((id) => buildMember({ id }));
    const relationships = [
      buildRelationship({ sourceMemberId: 'root', targetMemberId: 'parent' }),
      buildRelationship({ sourceMemberId: 'parent', targetMemberId: 'root' }),
      buildRelationship({ sourceMemberId: 'parent', targetMemberId: 'target' }),
      buildRelationship({ sourceMemberId: 'target', targetMemberId: 'parent' })
    ];

    expect(getAncestryPath(members, relationships, 'target').map((member) => member.id)).toEqual([
      'root',
      'parent',
      'target'
    ]);
  });

  it('returns the root itself and rejects missing or rootless targets', () => {
    const root = buildMember({ id: 'root' });
    const a = buildMember({ id: 'a' });
    const b = buildMember({ id: 'b' });
    const c = buildMember({ id: 'c' });

    expect(getAncestryPath([root], [], root.id)).toEqual([root]);
    expect(getAncestryPath([root], [], 'missing')).toEqual([]);
    expect(getAncestryPath([a, b, c], [
      buildRelationship({ sourceMemberId: a.id, targetMemberId: b.id }),
      buildRelationship({ sourceMemberId: b.id, targetMemberId: c.id }),
      buildRelationship({ sourceMemberId: c.id, targetMemberId: a.id })
    ], b.id)).toEqual([]);
  });
});
