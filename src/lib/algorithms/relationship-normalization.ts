import type { Relationship } from '@/data/types';

/**
 * Returns the persisted, canonical representation of relationships.
 *
 * Older releases stored an inverse row for every relationship. A logical
 * relationship is now represented by one row: directed ancestry rows keep
 * source=parent and target=child, while symmetric rows use a stable endpoint
 * order. The first row wins when an old reciprocal pair is encountered; the
 * legacy writer always wrote the canonical row first, so this preserves the
 * original direction during migration.
 */
export function normalizeRelationships(relationships: readonly Relationship[]): Relationship[] {
  const result: Relationship[] = [];
  const seen = new Set<string>();

  for (const relationship of relationships) {
    const normalized = normalizeRelationship(relationship);
    const key = logicalRelationshipKey(normalized);
    if (seen.has(key)) continue;
    seen.add(key);
    result.push(normalized);
  }
  return result;
}

export function normalizeRelationship(relationship: Relationship): Relationship {
  if (!isSymmetricType(relationship.type)) return relationship;
  if (relationship.sourceMemberId <= relationship.targetMemberId) return relationship;
  return {
    ...relationship,
    sourceMemberId: relationship.targetMemberId,
    targetMemberId: relationship.sourceMemberId
  };
}

export function logicalRelationshipKey(relationship: Relationship): string {
  // Every relationship between two members is one logical pair for
  // persistence. For directed ancestry rows the endpoint order is preserved
  // on the record itself, but sorted in the deduplication key so legacy
  // parent→child/child→parent pairs collapse to one canonical row.
  const endpoints = [relationship.sourceMemberId, relationship.targetMemberId].sort();
  return [
    relationship.type,
    endpoints[0],
    endpoints[1],
    relationship.customType ?? ''
  ].join('|');
}

export function isSymmetricType(type: Relationship['type']): boolean {
  return type === 'SPOUSE' || type === 'SIBLING' || type === 'CUSTOM';
}

export default normalizeRelationships;
