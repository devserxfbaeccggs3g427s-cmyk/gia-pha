import type { Member, Relationship } from '@/data/types';
import { getCanonicalParentChildEdges } from './ancestry';

export type GenerationMap = Map<string, number>;

/**
 * Calculates relative generations using a breadth-first traversal.
 * Members without a parent are roots (generation 0), spouses inherit the
 * same generation and children are assigned parent generation + 1.
 */
export function calculateGenerations(
  members: Member[],
  relationships: Relationship[]
): GenerationMap {
  const memberIds = new Set(members.map((member) => member.id));
  const parentToChildren = new Map<string, Set<string>>();
  const childIds = new Set<string>();
  const spouseByMember = new Map<string, Set<string>>();

  for (const { parentId, childId } of getCanonicalParentChildEdges(members, relationships)) {
    const children = parentToChildren.get(parentId) ?? new Set<string>();
    children.add(childId);
    parentToChildren.set(parentId, children);
    childIds.add(childId);
  }

  for (const relationship of relationships) {
    if (!memberIds.has(relationship.sourceMemberId) || !memberIds.has(relationship.targetMemberId)) {
      continue;
    }
    if (relationship.type === 'SPOUSE') {
      const sourceSpouses = spouseByMember.get(relationship.sourceMemberId) ?? new Set<string>();
      sourceSpouses.add(relationship.targetMemberId);
      spouseByMember.set(relationship.sourceMemberId, sourceSpouses);
      const targetSpouses = spouseByMember.get(relationship.targetMemberId) ?? new Set<string>();
      targetSpouses.add(relationship.sourceMemberId);
      spouseByMember.set(relationship.targetMemberId, targetSpouses);
    }
  }

  const generations: GenerationMap = new Map();
  const queue: Array<{ id: string; generation: number }> = [];
  for (const member of members) {
    if (!childIds.has(member.id)) queue.push({ id: member.id, generation: 0 });
  }

  // A malformed/cyclic graph can have no roots. Starting any unvisited node
  // at zero keeps the function total and makes the problematic component
  // visible to callers instead of silently dropping members.
  if (queue.length === 0 && members.length > 0) queue.push({ id: members[0].id, generation: 0 });

  while (queue.length > 0) {
    const { id, generation } = queue.shift()!;
    const existing = generations.get(id);
    if (existing !== undefined) {
      // A shorter path is the stable result for a graph with multiple roots.
      if (generation >= existing) continue;
      generations.set(id, generation);
    } else {
      generations.set(id, generation);
    }

    for (const spouseId of spouseByMember.get(id) ?? []) {
      const spouseGeneration = generations.get(spouseId);
      if (spouseGeneration === undefined || spouseGeneration !== generation) {
        queue.push({ id: spouseId, generation });
      }
    }
    for (const childId of parentToChildren.get(id) ?? []) {
      const childGeneration = generation + 1;
      const known = generations.get(childId);
      if (known === undefined || childGeneration < known) {
        queue.push({ id: childId, generation: childGeneration });
      }
    }
  }

  // Include isolated members and any nodes left in a malformed component.
  for (const member of members) {
    if (!generations.has(member.id)) generations.set(member.id, 0);
  }
  return generations;
}

export default calculateGenerations;
