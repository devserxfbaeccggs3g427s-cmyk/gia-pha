import type { Member, Relationship } from '@/data/types';
import { getCanonicalParentChildEdges } from './ancestry';

export type GenerationMap = Map<string, number>;

/**
 * Calculates relative generations after collapsing spouse-connected members
 * into components. A spouse without recorded parents therefore inherits the
 * component's generation instead of becoming an independent root.
 */
export function calculateGenerations(
  members: Member[],
  relationships: Relationship[]
): GenerationMap {
  const memberIds = new Set(members.map((member) => member.id));
  const components = new DisjointSet([...memberIds]);
  for (const relationship of relationships) {
    if (relationship.type !== 'SPOUSE') continue;
    if (!memberIds.has(relationship.sourceMemberId) || !memberIds.has(relationship.targetMemberId)) continue;
    components.union(relationship.sourceMemberId, relationship.targetMemberId);
  }

  const componentIds = new Set([...memberIds].map((id) => components.find(id)));
  const childrenByComponent = new Map<string, Set<string>>();
  const indegree = new Map<string, number>([...componentIds].map((id) => [id, 0]));
  const componentEdges = new Set<string>();

  for (const { parentId, childId } of getCanonicalParentChildEdges(members, relationships)) {
    const parentComponent = components.find(parentId);
    const childComponent = components.find(childId);
    // A parent-child edge inside one spouse component is malformed. It cannot
    // advance a generation and is ignored here; relationship validation should
    // reject it before this algorithm is called.
    if (parentComponent === childComponent) continue;
    const edgeKey = `${parentComponent}\u0000${childComponent}`;
    if (componentEdges.has(edgeKey)) continue;
    componentEdges.add(edgeKey);
    const children = childrenByComponent.get(parentComponent) ?? new Set<string>();
    children.add(childComponent);
    childrenByComponent.set(parentComponent, children);
    indegree.set(childComponent, (indegree.get(childComponent) ?? 0) + 1);
  }

  const generationByComponent = new Map<string, number>();
  const queue: string[] = [];
  for (const componentId of componentIds) {
    if ((indegree.get(componentId) ?? 0) === 0) {
      generationByComponent.set(componentId, 0);
      queue.push(componentId);
    }
  }

  // Keep the function total for malformed imported graphs. Valid data always
  // has at least one root component because cycle validation runs on writes.
  if (queue.length === 0 && componentIds.size > 0) {
    const first = [...componentIds][0];
    generationByComponent.set(first, 0);
    queue.push(first);
  }

  while (queue.length > 0) {
    const parentComponent = queue.shift()!;
    const parentGeneration = generationByComponent.get(parentComponent) ?? 0;
    for (const childComponent of childrenByComponent.get(parentComponent) ?? []) {
      const candidate = parentGeneration + 1;
      const current = generationByComponent.get(childComponent);
      // Multiple parents should normally have the same generation. Taking the
      // deepest constraint keeps malformed imported data deterministic while
      // valid trees still satisfy child = parent + 1 for every edge.
      if (current === undefined || candidate > current) {
        generationByComponent.set(childComponent, candidate);
      }
      indegree.set(childComponent, (indegree.get(childComponent) ?? 1) - 1);
      if ((indegree.get(childComponent) ?? 0) <= 0) queue.push(childComponent);
    }
  }

  return new Map(members.map((member) => [
    member.id,
    generationByComponent.get(components.find(member.id)) ?? 0
  ]));
}

class DisjointSet {
  private readonly parent = new Map<string, string>();

  constructor(ids: string[]) {
    for (const id of ids) this.parent.set(id, id);
  }

  find(id: string): string {
    const parent = this.parent.get(id);
    if (parent === undefined) {
      this.parent.set(id, id);
      return id;
    }
    if (parent === id) return id;
    const root = this.find(parent);
    this.parent.set(id, root);
    return root;
  }

  union(left: string, right: string): void {
    const leftRoot = this.find(left);
    const rightRoot = this.find(right);
    if (leftRoot === rightRoot) return;
    // Stable root selection makes maps and layouts deterministic.
    if (leftRoot < rightRoot) this.parent.set(rightRoot, leftRoot);
    else this.parent.set(leftRoot, rightRoot);
  }
}

export default calculateGenerations;
