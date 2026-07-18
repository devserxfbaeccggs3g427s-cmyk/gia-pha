import type { Member, Relationship } from '@/data/types';

/**
 * Returns the shortest valid parent-to-child path from any root ancestor to
 * the requested member. The order of members and relationships is used as a
 * deterministic tie-breaker when a member can be reached through two parents.
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
