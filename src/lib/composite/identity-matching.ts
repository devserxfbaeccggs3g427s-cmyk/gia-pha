import type { Member, Relationship, SourceReference } from '@/data/types';
import { normalizeVietnamese } from '@/lib/utils/vietnamese';

export interface IdentitySuggestion { references: [SourceReference, SourceReference]; memberNames: [string, string]; score: number; matchedFields: string[]; status: 'PROPOSED'; }
export function suggestIdentities(sources: Array<{ treeId: string; members: Member[]; relationships: Relationship[] }>): IdentitySuggestion[] {
  const suggestions: IdentitySuggestion[] = [];
  const neighborNames = sources.map((source) => buildNeighborNames(source.members, source.relationships));
  for (let left = 0; left < sources.length; left++) for (let right = left + 1; right < sources.length; right++) for (const a of sources[left].members) for (const b of sources[right].members) {
    const fields: string[] = []; let score = 0;
    if (normalizeVietnamese(a.fullName) === normalizeVietnamese(b.fullName)) { fields.push('fullName'); score += 50; }
    if (a.dateOfBirth && a.dateOfBirth === b.dateOfBirth) { fields.push('dateOfBirth'); score += 20; }
    if (a.placeOfBirth && b.placeOfBirth && normalizeVietnamese(a.placeOfBirth) === normalizeVietnamese(b.placeOfBirth)) { fields.push('placeOfBirth'); score += 15; }
    if (intersects(neighborNames[left].get(a.id), neighborNames[right].get(b.id))) { fields.push('neighboringRelationships'); score += 15; }
    if (score >= 50) suggestions.push({ references: [{ treeId: sources[left].treeId, memberId: a.id }, { treeId: sources[right].treeId, memberId: b.id }], memberNames: [a.fullName, b.fullName], score, matchedFields: fields, status: 'PROPOSED' });
  }
  return suggestions.sort((a, b) => b.score - a.score || suggestionKey(a).localeCompare(suggestionKey(b)));
}

function buildNeighborNames(members: Member[], relationships: Relationship[]): Map<string, Set<string>> {
  const names = new Map(members.map((member) => [member.id, normalizeVietnamese(member.fullName)]));
  const result = new Map<string, Set<string>>();
  for (const relationship of relationships) for (const [memberId, neighborId] of [[relationship.sourceMemberId, relationship.targetMemberId], [relationship.targetMemberId, relationship.sourceMemberId]]) {
    const name = names.get(neighborId);
    if (name) result.set(memberId, new Set([...(result.get(memberId) ?? []), name]));
  }
  return result;
}
function intersects(left?: Set<string>, right?: Set<string>): boolean { return Boolean(left && right && [...left].some((value) => right.has(value))); }
function suggestionKey(value: IdentitySuggestion): string { return value.references.map((ref) => `${ref.treeId}:${ref.memberId}`).join('|'); }
