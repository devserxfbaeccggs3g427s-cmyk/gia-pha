import type { Member, Relationship } from '@/data/types';

/**
 * Returns the shortest valid parent-to-child path from any root ancestor to
 * the requested member. Kept for callers that need a linear path; lineage
 * views should use getAncestrySubgraph so no parent branch is lost.
 */
export function getAncestryPath(
  members: readonly Member[],
  relationships: readonly Relationship[],
  targetMemberId: string
): Member[] {
  if (!targetMemberId) return [];

  const memberById = new Map(members.map((member) => [member.id, member]));
  if (!memberById.has(targetMemberId)) return [];

  const edges = getCanonicalParentChildEdges(members, relationships);
  const childIds = new Set(edges.map((edge) => edge.childId));
  const childrenByParent = new Map<string, string[]>();

  for (const { parentId, childId } of edges) {
    const children = childrenByParent.get(parentId) ?? [];
    if (!children.includes(childId)) children.push(childId);
    childrenByParent.set(parentId, children);
  }

  const roots = members.filter((member) => !childIds.has(member.id));
  const queue = roots.map((member) => member.id);
  const visited = new Set(queue);
  const predecessor = new Map<string, string>();

  while (queue.length > 0) {
    const current = queue.shift()!;
    if (current === targetMemberId) {
      return materializePath(targetMemberId, predecessor, memberById);
    }

    for (const childId of childrenByParent.get(current) ?? []) {
      if (visited.has(childId)) continue;
      visited.add(childId);
      predecessor.set(childId, current);
      queue.push(childId);
    }
  }

  // A member in a malformed rootless/cyclic component has no valid ancestry
  // path as defined by the specification.
  return [];
}

export interface ParentChildEdge {
  parentId: string;
  childId: string;
}

export interface SpouseEdge {
  sourceMemberId: string;
  targetMemberId: string;
}

export interface AncestrySubgraph {
  targetMemberId: string;
  memberIds: string[];
  parentChildEdges: ParentChildEdge[];
  spouseEdges: SpouseEdge[];
}

/**
 * Returns the complete ancestor subgraph for a member instead of choosing one
 * shortest path. Every parent branch is traversed recursively. Spouses of the
 * target and its ancestors are included as contextual nodes/edges, but their
 * parents are not traversed unless they are also connected through a canonical
 * parent-child edge. This keeps in-law context visible without turning a
 * lineage view into the entire extended family graph.
 */
export function getAncestrySubgraph(
  members: readonly Member[],
  relationships: readonly Relationship[],
  targetMemberId: string,
  options: { includeSpouses?: boolean } = {}
): AncestrySubgraph {
  const memberIds = new Set(members.map((member) => member.id));
  const empty: AncestrySubgraph = {
    targetMemberId,
    memberIds: [],
    parentChildEdges: [],
    spouseEdges: []
  };
  if (!targetMemberId || !memberIds.has(targetMemberId)) return empty;

  const parentChildEdges = getCanonicalParentChildEdges(members, relationships);
  const parentsByChild = new Map<string, ParentChildEdge[]>();
  for (const edge of parentChildEdges) {
    const parents = parentsByChild.get(edge.childId) ?? [];
    parents.push(edge);
    parentsByChild.set(edge.childId, parents);
  }

  const selected = new Set<string>([targetMemberId]);
  const selectedParentEdges = new Map<string, ParentChildEdge>();
  const queue = [targetMemberId];
  while (queue.length > 0) {
    const childId = queue.shift()!;
    for (const edge of parentsByChild.get(childId) ?? []) {
      const key = edgeKey(edge.parentId, edge.childId);
      if (!selectedParentEdges.has(key)) selectedParentEdges.set(key, edge);
      if (!selected.has(edge.parentId)) {
        selected.add(edge.parentId);
        queue.push(edge.parentId);
      }
    }
  }

  const spouseEdges: SpouseEdge[] = [];
  if (options.includeSpouses !== false) {
    const ancestorMemberIds = new Set(selected);
    const spouseKeys = new Set<string>();
    for (const relationship of relationships) {
      if (relationship.type !== 'SPOUSE') continue;
      if (!memberIds.has(relationship.sourceMemberId) || !memberIds.has(relationship.targetMemberId)) continue;
      if (!ancestorMemberIds.has(relationship.sourceMemberId) && !ancestorMemberIds.has(relationship.targetMemberId)) continue;
      const sourceMemberId = relationship.sourceMemberId;
      const targetMemberId = relationship.targetMemberId;
      const key = [sourceMemberId, targetMemberId].sort().join('\u0000');
      if (spouseKeys.has(key)) continue;
      spouseKeys.add(key);
      spouseEdges.push({ sourceMemberId, targetMemberId });
      selected.add(sourceMemberId);
      selected.add(targetMemberId);
    }
  }

  return {
    targetMemberId,
    memberIds: members.filter((member) => selected.has(member.id)).map((member) => member.id),
    parentChildEdges: [...selectedParentEdges.values()],
    spouseEdges
  };
}

export function getCanonicalParentChildEdges(
  members: readonly Member[],
  relationships: readonly Relationship[]
): ParentChildEdge[] {
  const memberById = new Map(members.map((member) => [member.id, member]));
  const edges: ParentChildEdge[] = [];
  const accepted = new Set<string>();

  for (const relationship of relationships) {
    if (relationship.type !== 'PARENT_CHILD') continue;
    const source = memberById.get(relationship.sourceMemberId);
    const target = memberById.get(relationship.targetMemberId);
    if (!source || !target || source.id === target.id) continue;

    const key = edgeKey(source.id, target.id);
    const reverseKey = edgeKey(target.id, source.id);
    if (accepted.has(key) || accepted.has(reverseKey)) continue;

    // Legacy reciprocal rows are tolerated during migration. New persistence
    // contains only the canonical parent -> child direction.
    accepted.add(key);
    edges.push({ parentId: source.id, childId: target.id });
  }

  return edges;
}

function edgeKey(sourceId: string, targetId: string): string {
  return JSON.stringify([sourceId, targetId]);
}

function materializePath(
  targetMemberId: string,
  predecessor: ReadonlyMap<string, string>,
  memberById: ReadonlyMap<string, Member>
): Member[] {
  const ids: string[] = [];
  let current: string | undefined = targetMemberId;
  while (current !== undefined) {
    ids.push(current);
    current = predecessor.get(current);
  }
  ids.reverse();
  return ids.map((id) => memberById.get(id)).filter((member): member is Member => Boolean(member));
}

export default getAncestryPath;
