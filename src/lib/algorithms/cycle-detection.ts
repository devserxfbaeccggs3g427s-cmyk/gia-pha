import type { Relationship } from '@/data/types';
import { normalizeRelationships } from './relationship-normalization';

/**
 * Returns true when adding `proposedSource -> proposedTarget` as a
 * PARENT_CHILD edge would introduce a cycle.
 *
 * Only canonical parent→child edges participate in ancestry. Normalizing here
 * also keeps validation safe for legacy input that still contains reciprocal
 * rows before the blob migration has completed.
 */
export function detectCycles(
  relationships: Relationship[],
  proposedSource: string,
  proposedTarget: string
): boolean {
  if (!proposedSource || !proposedTarget || proposedSource === proposedTarget) return true;

  const parentToChildren = new Map<string, Set<string>>();
  for (const relationship of normalizeRelationships(relationships)) {
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
