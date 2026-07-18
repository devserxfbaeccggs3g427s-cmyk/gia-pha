import { describe, expect, it } from 'vitest';
import { buildTreeLayout, TREE_NODE_DIMENSIONS } from '@/components/tree/tree-layout';
import { buildMember, buildRelationship } from '../../utils/factories';

function familyFixture() {
  const members = [
    buildMember({ id: 'root', fullName: 'Root' }),
    buildMember({ id: 'spouse', fullName: 'Spouse', gender: 'FEMALE' }),
    buildMember({ id: 'child-a', fullName: 'Child A' }),
    buildMember({ id: 'child-b', fullName: 'Child B' }),
    buildMember({ id: 'grandchild', fullName: 'Grandchild' })
  ];
  const relationships = [
    buildRelationship({ sourceMemberId: 'root', targetMemberId: 'spouse', type: 'SPOUSE' }),
    buildRelationship({ sourceMemberId: 'root', targetMemberId: 'child-a' }),
    buildRelationship({ sourceMemberId: 'root', targetMemberId: 'child-b' }),
    buildRelationship({ sourceMemberId: 'child-a', targetMemberId: 'grandchild' })
  ];
  return { members, relationships };
}

describe('buildTreeLayout', () => {
  it('places generations in vertical layers without overlapping siblings', () => {
    const { members, relationships } = familyFixture();
    const positions = buildTreeLayout(members, relationships, 'vertical', 'detailed');
    const byId = new Map(positions.map((item) => [item.id, item]));

    expect(byId.get('root')?.generation).toBe(0);
    expect(byId.get('spouse')?.generation).toBe(0);
    expect(byId.get('child-a')?.generation).toBe(1);
    expect(byId.get('grandchild')?.generation).toBe(2);
    expect(byId.get('child-a')?.position.y).toBeGreaterThan(byId.get('root')!.position.y);
    expect(byId.get('grandchild')?.position.y).toBeGreaterThan(byId.get('child-a')!.position.y);
    expect(Math.abs(byId.get('child-a')!.position.x - byId.get('child-b')!.position.x))
      .toBeGreaterThanOrEqual(TREE_NODE_DIMENSIONS.detailed.width);
  });

  it('rotates the generation axis for horizontal layout', () => {
    const { members, relationships } = familyFixture();
    const positions = buildTreeLayout(members, relationships, 'horizontal', 'compact');
    const byId = new Map(positions.map((item) => [item.id, item]));

    expect(byId.get('child-a')?.position.x).toBeGreaterThan(byId.get('root')!.position.x);
    expect(byId.get('grandchild')?.position.x).toBeGreaterThan(byId.get('child-a')!.position.x);
    expect(Math.abs(byId.get('child-a')!.position.y - byId.get('child-b')!.position.y))
      .toBeGreaterThanOrEqual(TREE_NODE_DIMENSIONS.compact.height);
  });

  it('creates a deterministic radial fan with the root inside descendant rings', () => {
    const { members, relationships } = familyFixture();
    const first = buildTreeLayout(members, relationships, 'fan');
    const second = buildTreeLayout(members, relationships, 'fan');
    const byId = new Map(first.map((item) => [item.id, item]));
    const root = byId.get('root')!.position;
    const child = byId.get('child-a')!.position;
    const grandchild = byId.get('grandchild')!.position;
    const distance = (point: { x: number; y: number }) => Math.hypot(point.x - root.x, point.y - root.y);

    expect(first).toEqual(second);
    expect(distance(child)).toBeGreaterThan(100);
    expect(distance(grandchild)).toBeGreaterThan(distance(child));
    expect(new Set(first.map(({ position }) => `${position.x}:${position.y}`)).size).toBe(first.length);
  });

  it('returns an empty layout for an empty tree', () => {
    expect(buildTreeLayout([], [], 'vertical')).toEqual([]);
  });
});
