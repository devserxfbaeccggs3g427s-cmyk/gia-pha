import type { Relationship } from '@/data/types';

/**
 * Returns true when adding `proposedSource -> proposedTarget` as a
 * PARENT_CHILD edge would introduce a cycle.
 *
 * Relationships are persisted in both directions by RelationshipService, but
 * the algorithm still treats each directed PARENT_CHILD edge as ancestry.
 * The service handles an already-present inverse as an idempotent retry before
 * invoking this algorithm.
 */
export function detectCycles(
  relationships: Relationship[],
  proposedSource: string,
  proposedTarget: string
): boolean {
  if (!proposedSource || !proposedTarget || proposedSource === proposedTarget) return true;

  const parentToChildren = new Map<string, Set<string>>();
  for (const relationship of relationships) {
    if (relationship.type !== 'PARENT_CHILD') continue;
    const children = parentToChildren.get(relationship.sourceMemberId) ?? new Set<string>();
    children.add(relationship.targetMemberId);
    parentToChildren.set(relationship.sourceMemberId, children);
  }

  // Adding source -> target is cyclic exactly when source is already
  // reachable from target through parent -> child edges.
  const visited = new Set<string>();
  const stack = [proposedTarget];
  while (stack.length > 0) {
    const current = stack.pop()!;
    if (current === proposedSource) return true;
    if (visited.has(current)) continue;
    visited.add(current);
    for (const child of parentToChildren.get(current) ?? []) {
      if (!visited.has(child)) stack.push(child);
    }
  }
  return false;
}

export default detectCycles;
