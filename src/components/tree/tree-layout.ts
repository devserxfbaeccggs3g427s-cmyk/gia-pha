import type { Member, Relationship } from '@/data/types';
import { getCanonicalParentChildEdges } from '@/lib/algorithms/ancestry';
import { calculateGenerations } from '@/lib/algorithms/generation';

export type TreeLayoutMode = 'vertical' | 'horizontal' | 'fan';
export type TreeDisplayMode = 'compact' | 'detailed';

export interface TreeNodePosition {
  id: string;
  generation: number;
  position: { x: number; y: number };
}

export interface TreeLayoutDimensions {
  width: number;
  height: number;
  layerGap: number;
  nodeGap: number;
}

export const TREE_NODE_DIMENSIONS: Record<TreeDisplayMode, TreeLayoutDimensions> = {
  compact: { width: 190, height: 86, layerGap: 92, nodeGap: 28 },
  detailed: { width: 236, height: 118, layerGap: 112, nodeGap: 36 }
};

/**
 * Builds stable, generation-aware positions without relying on browser APIs so
 * layouts can be prepared and verified independently from React Flow.
 */
export function buildTreeLayout(
  members: readonly Member[],
  relationships: readonly Relationship[],
  mode: TreeLayoutMode,
  displayMode: TreeDisplayMode = 'detailed'
): TreeNodePosition[] {
  if (members.length === 0) return [];

  const dimensions = TREE_NODE_DIMENSIONS[displayMode];
  const generationMap = calculateGenerations([...members], [...relationships]);
  const orderedLayers = buildOrderedLayers(members, relationships, generationMap);

  if (mode === 'fan') {
    return buildFanLayout(orderedLayers, dimensions);
  }

  const nodeAcross = mode === 'vertical' ? dimensions.width : dimensions.height;
  const nodeDown = mode === 'vertical' ? dimensions.height : dimensions.width;
  const widestLayer = Math.max(...orderedLayers.map(([, layer]) => layer.length));
  const layerSpan = widestLayer * nodeAcross + Math.max(0, widestLayer - 1) * dimensions.nodeGap;
  const positions: TreeNodePosition[] = [];

  for (const [generation, layer] of orderedLayers) {
    const currentSpan = layer.length * nodeAcross + Math.max(0, layer.length - 1) * dimensions.nodeGap;
    const offset = (layerSpan - currentSpan) / 2;

    layer.forEach((member, index) => {
      const across = offset + index * (nodeAcross + dimensions.nodeGap);
      const down = generation * (nodeDown + dimensions.layerGap);
      positions.push({
        id: member.id,
        generation,
        position: mode === 'vertical' ? { x: across, y: down } : { x: down, y: across }
      });
    });
  }

  return normalizePositions(positions);
}

function buildOrderedLayers(
  members: readonly Member[],
  relationships: readonly Relationship[],
  generationMap: ReadonlyMap<string, number>
): Array<[number, Member[]]> {
  const memberOrder = new Map(members.map((member, index) => [member.id, index]));
  const parentsByChild = new Map<string, string[]>();
  const spousesByMember = new Map<string, Set<string>>();

  for (const edge of getCanonicalParentChildEdges(members, relationships)) {
    const parents = parentsByChild.get(edge.childId) ?? [];
    parents.push(edge.parentId);
    parentsByChild.set(edge.childId, parents);
  }

  for (const relationship of relationships) {
    if (relationship.type !== 'SPOUSE') continue;
    addSetValue(spousesByMember, relationship.sourceMemberId, relationship.targetMemberId);
    addSetValue(spousesByMember, relationship.targetMemberId, relationship.sourceMemberId);
  }

  const layers = new Map<number, Member[]>();
  for (const member of members) {
    const generation = generationMap.get(member.id) ?? member.generation ?? 0;
    const layer = layers.get(generation) ?? [];
    layer.push(member);
    layers.set(generation, layer);
  }

  const ordered = [...layers.entries()].sort(([left], [right]) => left - right);
  const previousIndexes = new Map<string, number>();

  for (const [, layer] of ordered) {
    const spouseGroups = buildSpouseGroups(layer, spousesByMember, memberOrder);
    layer.splice(0, layer.length, ...spouseGroups
      .sort((left, right) => {
        const leftScore = Math.min(...left.map((member) => familyOrderScore(
          member.id, parentsByChild, spousesByMember, previousIndexes, memberOrder
        )));
        const rightScore = Math.min(...right.map((member) => familyOrderScore(
          member.id, parentsByChild, spousesByMember, previousIndexes, memberOrder
        )));
        return leftScore - rightScore
          || (memberOrder.get(left[0].id) ?? 0) - (memberOrder.get(right[0].id) ?? 0);
      })
      .flat());
    layer.forEach((member, index) => previousIndexes.set(member.id, index));
  }

  return ordered;
}

/**
 * Treat each spouse-connected set in a generation as one layout block. This
 * guarantees that a spouse edge is not crossed by an unrelated node in the
 * same generation, while preserving deterministic member ordering.
 */
function buildSpouseGroups(
  layer: readonly Member[],
  spousesByMember: ReadonlyMap<string, Set<string>>,
  memberOrder: ReadonlyMap<string, number>
): Member[][] {
  const memberById = new Map(layer.map((member) => [member.id, member]));
  const unvisited = new Set(memberById.keys());
  const groups: Member[][] = [];

  while (unvisited.size > 0) {
    const start = unvisited.values().next().value as string;
    const queue = [start];
    const group: Member[] = [];
    unvisited.delete(start);

    while (queue.length > 0) {
      const current = queue.shift()!;
      const member = memberById.get(current);
      if (member) group.push(member);
      for (const spouseId of spousesByMember.get(current) ?? []) {
        if (!unvisited.has(spouseId)) continue;
        unvisited.delete(spouseId);
        queue.push(spouseId);
      }
    }

    group.sort((left, right) => (memberOrder.get(left.id) ?? 0) - (memberOrder.get(right.id) ?? 0));
    groups.push(group);
  }

  return groups;
}

function buildFanLayout(
  layers: Array<[number, Member[]]>,
  dimensions: TreeLayoutDimensions
): TreeNodePosition[] {
  const positions: TreeNodePosition[] = [];
  const maxGeneration = Math.max(...layers.map(([generation]) => generation));
  const outerRadius = Math.max(320, maxGeneration * (dimensions.width + 74));
  const center = outerRadius + dimensions.width;

  for (const [generation, layer] of layers) {
    if (generation === 0) {
      const totalWidth = layer.length * dimensions.width + Math.max(0, layer.length - 1) * dimensions.nodeGap;
      layer.forEach((member, index) => {
        positions.push({
          id: member.id,
          generation,
          position: {
            x: center - totalWidth / 2 + index * (dimensions.width + dimensions.nodeGap),
            y: center - dimensions.height / 2
          }
        });
      });
      continue;
    }

    const radius = generation * (dimensions.width + 74);
    const arc = Math.min(Math.PI * 1.76, Math.max(Math.PI * 0.9, layer.length * 0.52));
    const startAngle = -Math.PI / 2 - arc / 2;

    layer.forEach((member, index) => {
      const fraction = layer.length === 1 ? 0.5 : index / (layer.length - 1);
      const angle = startAngle + arc * fraction;
      positions.push({
        id: member.id,
        generation,
        position: {
          x: center + Math.cos(angle) * radius - dimensions.width / 2,
          y: center + Math.sin(angle) * radius - dimensions.height / 2
        }
      });
    });
  }

  return normalizePositions(positions);
}

function normalizePositions(positions: TreeNodePosition[]): TreeNodePosition[] {
  const minX = Math.min(...positions.map(({ position }) => position.x));
  const minY = Math.min(...positions.map(({ position }) => position.y));
  const padding = 64;
  return positions.map((node) => ({
    ...node,
    position: { x: node.position.x - minX + padding, y: node.position.y - minY + padding }
  }));
}

function familyOrderScore(
  memberId: string,
  parentsByChild: ReadonlyMap<string, string[]>,
  spousesByMember: ReadonlyMap<string, Set<string>>,
  knownIndexes: ReadonlyMap<string, number>,
  fallbackIndexes: ReadonlyMap<string, number>
): number {
  const related = [
    ...(parentsByChild.get(memberId) ?? []),
    ...[...(spousesByMember.get(memberId) ?? [])]
  ];
  const indexes = related
    .map((id) => knownIndexes.get(id))
    .filter((value): value is number => value !== undefined);
  if (indexes.length > 0) return indexes.reduce((sum, value) => sum + value, 0) / indexes.length;
  return 10_000 + (fallbackIndexes.get(memberId) ?? 0);
}

function addSetValue(map: Map<string, Set<string>>, key: string, value: string): void {
  const values = map.get(key) ?? new Set<string>();
  values.add(value);
  map.set(key, values);
}
