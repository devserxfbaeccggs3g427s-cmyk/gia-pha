import type { Event, MediaMetadata, Member, Relationship } from '@/data/types';
import type { SourceScopeInput } from '@/data/schemas';
import { getCanonicalParentChildEdges } from './ancestry';

export type SourceScopeWarningCode = 'INVALID_SCOPE' | 'INVALID_REFERENCE' | 'REFERENCE_OUT_OF_SCOPE';

export interface SourceScopeWarning {
  code: SourceScopeWarningCode;
  message: string;
  entityId?: string;
}

export interface SourceScopeResult {
  members: Member[];
  relationships: Relationship[];
  events: Event[];
  mediaMetadata: MediaMetadata[];
  warnings: SourceScopeWarning[];
}

export type SourceScopeOptions = Pick<
  SourceScopeInput,
  'scope' | 'anchorMemberIds' | 'selectedMemberIds' | 'includeSpouses' | 'includeEvents' | 'includeMedia'
>;

export function resolveSourceScope(
  members: readonly Member[],
  relationships: readonly Relationship[],
  events: readonly Event[],
  mediaMetadata: readonly MediaMetadata[],
  options: SourceScopeOptions
): SourceScopeResult {
  const memberIds = new Set(members.map((member) => member.id));
  const scopedIds = new Set<string>();
  const warnings: SourceScopeWarning[] = [];

  if (options.scope === 'FULL_TREE') {
    for (const member of members) scopedIds.add(member.id);
  } else if (options.scope === 'DESCENDANTS') {
    const validAnchors = options.anchorMemberIds.filter((id) => {
      if (memberIds.has(id)) return true;
      warnings.push({ code: 'INVALID_REFERENCE', message: `Anchor member "${id}" does not exist in the source tree`, entityId: id });
      return false;
    });

    if (validAnchors.length === 0) {
      warnings.push({ code: 'INVALID_SCOPE', message: 'DESCENDANTS scope has no valid source-tree anchor' });
    } else {
      const childrenByParent = new Map<string, string[]>();
      for (const edge of getCanonicalParentChildEdges(members, relationships)) {
        const children = childrenByParent.get(edge.parentId) ?? [];
        children.push(edge.childId);
        childrenByParent.set(edge.parentId, children);
      }
      const queue = [...validAnchors];
      for (const anchorId of validAnchors) scopedIds.add(anchorId);
      while (queue.length > 0) {
        const memberId = queue.shift()!;
        for (const childId of childrenByParent.get(memberId) ?? []) {
          if (scopedIds.has(childId)) continue;
          scopedIds.add(childId);
          queue.push(childId);
        }
      }
    }
  } else if (options.scope === 'SELECTED_MEMBERS') {
    for (const selectedId of options.selectedMemberIds) {
      if (memberIds.has(selectedId)) {
        scopedIds.add(selectedId);
      } else {
        warnings.push({ code: 'REFERENCE_OUT_OF_SCOPE', message: `Selected member "${selectedId}" does not exist in the source tree`, entityId: selectedId });
      }
    }
  } else {
    warnings.push({ code: 'INVALID_SCOPE', message: `Unsupported source scope: ${String(options.scope)}` });
  }

  if (options.includeSpouses) {
    const baseScope = new Set(scopedIds);
    for (const relationship of relationships) {
      if (relationship.type !== 'SPOUSE') continue;
      const sourceInScope = baseScope.has(relationship.sourceMemberId);
      const targetInScope = baseScope.has(relationship.targetMemberId);
      if (!sourceInScope && !targetInScope) continue;
      if (sourceInScope && memberIds.has(relationship.targetMemberId)) scopedIds.add(relationship.targetMemberId);
      if (targetInScope && memberIds.has(relationship.sourceMemberId)) scopedIds.add(relationship.sourceMemberId);
    }
  }

  const scopedMembers = members.filter((member) => scopedIds.has(member.id));
  const scopedRelationships = relationships.filter(
    (relationship) => scopedIds.has(relationship.sourceMemberId) && scopedIds.has(relationship.targetMemberId)
  );
  const associatedEvents = events.filter((event) => event.memberIds.some((memberId) => scopedIds.has(memberId)));
  const scopedEvents = options.includeEvents ? associatedEvents : [];
  const scopedEventIds = new Set(scopedEvents.map((event) => event.id));
  const eventMediaIds = new Set(scopedEvents.flatMap((event) => event.mediaIds));
  const scopedMedia = options.includeMedia
    ? mediaMetadata.filter((media) => {
        const linkedMemberIds = [...(media.memberIds ?? []), ...(media.memberId ? [media.memberId] : [])];
        const linkedEventIds = [...(media.eventIds ?? []), ...(media.eventId ? [media.eventId] : [])];
        return linkedMemberIds.some((id) => scopedIds.has(id))
          || linkedEventIds.some((id) => scopedEventIds.has(id))
          || eventMediaIds.has(media.id);
      })
    : [];

  return {
    members: scopedMembers,
    relationships: scopedRelationships,
    events: scopedEvents,
    mediaMetadata: scopedMedia,
    warnings
  };
}
