import { nanoid } from 'nanoid';
import { ZodError } from 'zod';
import { createMemberSchema, updateMemberSchema } from '@/data/schemas';
import type {
  Event,
  MediaMetadata,
  Member,
  Relationship
} from '@/data/types';
import { getEvents, getMediaMetadata, getMembers, getRelationships } from '@/lib/blob/readers';
import { deleteBlobs } from '@/lib/blob/client';
import {
  putEvents,
  putMediaMetadata,
  putMembers,
  putRelationships
} from '@/lib/blob/writers';
import { changeLogService } from './changelog-service';

export type MemberMutationActor = string | { userId?: string } | undefined;

export interface MemberFull extends Member {
  /** The nested form is convenient for API consumers that expect a resource envelope. */
  member: Member;
  relationships: Relationship[];
  relatedMembers: Member[];
  events: Event[];
  media: MediaMetadata[];
  status: 'ALIVE' | 'DECEASED';
  lifespan: number | null;
}

export interface DeleteMemberResult {
  member: Member;
  affectedRelationships: Relationship[];
  /** Alias used by clients that call the list `deletedRelationships`. */
  deletedRelationships: Relationship[];
  affectedEvents: Event[];
  deletedMedia: MediaMetadata[];
}

export interface DuplicateSearchCriteria {
  memberId?: string;
  fullName?: string;
  name?: string;
  firstName?: string;
  lastName?: string;
  dateOfBirth?: string;
  placeOfBirth?: string;
}

export interface MemberPair {
  first: Member;
  second: Member;
  /** Both names are provided to make this result convenient in UI code. */
  members: [Member, Member];
  score: number;
  matchingFields: Array<'name' | 'dateOfBirth' | 'placeOfBirth'>;
}

export type MergeStrategy =
  | 'preferSource'
  | 'preferTarget'
  | 'nonEmpty'
  | 'SOURCE_WINS'
  | 'TARGET_WINS'
  | 'PREFER_SOURCE'
  | 'PREFER_TARGET'
  | { prefer?: 'source' | 'target' | 'nonEmpty'; sourceWins?: boolean; targetWins?: boolean };

export class MemberServiceError extends Error {
  constructor(
    public readonly code: 'NOT_FOUND' | 'INVALID_INPUT' | 'CONFLICT',
    message: string
  ) {
    super(message);
    this.name = 'MemberServiceError';
  }
}

export class MemberService {
  calculateLifespan(
    dateOfBirth?: string | Pick<Member, 'dateOfBirth' | 'dateOfDeath'>,
    dateOfDeath?: string | Date,
    now?: Date
  ): number | null {
    return calculateLifespan(dateOfBirth, dateOfDeath, now);
  }

  getMemberStatus(member: Pick<Member, 'isAlive' | 'dateOfBirth' | 'dateOfDeath'>, now?: Date) {
    return getMemberStatus(member, now);
  }

  async createMember(
    treeId: string,
    data: unknown,
    actor: MemberMutationActor = undefined
  ): Promise<Member> {
    const input = createMemberSchema.parse(data);
    validateDates(input.dateOfBirth, input.dateOfDeath);
    const [members, media] = await Promise.all([getMembers(treeId), getMediaMetadata(treeId)]);
    validateAvatarMedia(input.avatarMediaId, media);
    const now = new Date().toISOString();
    const member: Member = {
      ...input,
      id: nanoid(),
      treeId,
      // A death date is authoritative; this prevents contradictory status data.
      isAlive: input.dateOfDeath ? false : input.isAlive,
      createdAt: now,
      updatedAt: now
    };

    members.push(member);
    await putMembers(treeId, members);
    await changeLogService.recordChange({
      treeId,
      userId: actorId(actor),
      memberId: member.id,
      action: 'CREATE',
      newData: memberToData(member),
      createdAt: now
    });
    return member;
  }

  async updateMember(
    treeId: string,
    memberId: string,
    data: unknown,
    actor: MemberMutationActor = undefined
  ): Promise<Member> {
    const input = updateMemberSchema.parse(data);
    const [members, media] = await Promise.all([getMembers(treeId), getMediaMetadata(treeId)]);
    const index = members.findIndex((member) => member.id === memberId);
    if (index < 0) throw new MemberServiceError('NOT_FOUND', 'Member not found');
    validateAvatarMedia(input.avatarMediaId, media);

    const current = members[index];
    const nextDateOfBirth = input.dateOfBirth ?? current.dateOfBirth;
    const nextDateOfDeath = input.dateOfDeath ?? current.dateOfDeath;
    validateDates(nextDateOfBirth, nextDateOfDeath);
    const next: Member = {
      ...current,
      ...input,
      id: current.id,
      treeId: current.treeId,
      createdAt: current.createdAt,
      updatedAt: new Date().toISOString(),
      isAlive: nextDateOfDeath ? false : input.isAlive ?? current.isAlive
    };
    const changedFields = Object.keys(current).filter(
      (key) =>
        key !== 'updatedAt' &&
        !sameJson(
          (current as unknown as Record<string, unknown>)[key],
          (next as unknown as Record<string, unknown>)[key]
        )
    );

    if (changedFields.length === 0) return current;
    members[index] = next;
    await putMembers(treeId, members);
    await changeLogService.recordChange({
      treeId,
      userId: actorId(actor),
      memberId,
      action: 'UPDATE',
      previousData: memberToData(current),
      newData: memberToData(next),
      fieldChanged: changedFields.join(','),
      createdAt: next.updatedAt
    });
    return next;
  }

  async deleteMember(
    treeId: string,
    memberId: string,
    actor: MemberMutationActor = undefined
  ): Promise<DeleteMemberResult> {
    const [members, relationships, events, media] = await Promise.all([
      getMembers(treeId),
      getRelationships(treeId),
      getEvents(treeId),
      getMediaMetadata(treeId)
    ]);
    const member = members.find((candidate) => candidate.id === memberId);
    if (!member) throw new MemberServiceError('NOT_FOUND', 'Member not found');

    const affectedRelationships = relationships.filter(
      (relationship) =>
        relationship.sourceMemberId === memberId || relationship.targetMemberId === memberId
    );
    const affectedEvents = events.filter((event) => event.memberIds.includes(memberId));
    const memberMedia = media.filter((item) => mediaMemberIds(item).includes(memberId));
    const deletedMedia = memberMedia.filter((item) => {
      const remainingMembers = mediaMemberIds(item).filter((id) => id !== memberId);
      const hasOtherLinks = remainingMembers.length > 0 || mediaEventIds(item).length > 0 || Boolean(item.albumId);
      return !hasOtherLinks;
    });

    await putMembers(
      treeId,
      members.filter((candidate) => candidate.id !== memberId)
    );
    if (affectedRelationships.length > 0) {
      await putRelationships(
        treeId,
        relationships.filter(
          (relationship) =>
            relationship.sourceMemberId !== memberId && relationship.targetMemberId !== memberId
        )
      );
    }
    if (affectedEvents.length > 0) {
      const updatedEvents = events.map((event) =>
        event.memberIds.includes(memberId)
          ? { ...event, memberIds: event.memberIds.filter((id) => id !== memberId), updatedAt: new Date().toISOString() }
          : event
      );
      await putEvents(treeId, updatedEvents);
    }
    if (memberMedia.length > 0) {
      const deletedIds = new Set(deletedMedia.map((item) => item.id));
      await putMediaMetadata(treeId, media
        .filter((item) => !deletedIds.has(item.id))
        .map((item) => mediaMemberIds(item).includes(memberId)
          ? {
              ...item,
              memberIds: mediaMemberIds(item).filter((id) => id !== memberId),
              ...(item.memberId === memberId ? { memberId: undefined } : {})
            }
          : item
        ));
      if (deletedMedia.length > 0) {
        try {
          await deleteBlobs(deletedMedia.flatMap((item) => [
            item.blobUrl,
            ...(item.thumbnailUrl ? [item.thumbnailUrl] : [])
          ]));
        } catch (error) {
          console.error(`[members] failed to clean up media for ${memberId}`, error);
        }
      }
    }

    await changeLogService.recordChange({
      treeId,
      userId: actorId(actor),
      memberId,
      action: 'DELETE',
      previousData: memberToData(member)
    });
    return {
      member,
      affectedRelationships,
      deletedRelationships: affectedRelationships,
      affectedEvents,
      deletedMedia
    };
  }

  async getMemberWithRelations(treeId: string, memberId: string): Promise<MemberFull> {
    const [members, relationships, events, media] = await Promise.all([
      getMembers(treeId),
      getRelationships(treeId),
      getEvents(treeId),
      getMediaMetadata(treeId)
    ]);
    const member = members.find((candidate) => candidate.id === memberId);
    if (!member) throw new MemberServiceError('NOT_FOUND', 'Member not found');
    const memberRelationships = relationships.filter(
      (relationship) =>
        relationship.sourceMemberId === memberId || relationship.targetMemberId === memberId
    );
    const relatedIds = new Set(
      memberRelationships.map((relationship) =>
        relationship.sourceMemberId === memberId
          ? relationship.targetMemberId
          : relationship.sourceMemberId
      )
    );
    return {
      ...member,
      member,
      relationships: memberRelationships,
      relatedMembers: members.filter((candidate) => relatedIds.has(candidate.id)),
      events: events.filter((event) => event.memberIds.includes(memberId)),
      media: media.filter((item) => mediaMemberIds(item).includes(memberId)),
      ...getMemberStatus(member)
    };
  }

  async findDuplicates(treeId: string, criteria: DuplicateSearchCriteria = {}): Promise<MemberPair[]> {
    const members = await getMembers(treeId);
    const requestedMember = criteria.memberId
      ? members.find((member) => member.id === criteria.memberId)
      : undefined;
    if (criteria.memberId && !requestedMember) return [];
    const candidates = members.filter((member) => {
      if (criteria.memberId && member.id === criteria.memberId) return false;
      return matchesCriteria(member, criteria);
    });
    const pairs: MemberPair[] = [];
    if (requestedMember) {
      for (const candidate of candidates) {
        const matchingFields = matchingMemberFields(requestedMember, candidate);
        if (matchingFields.length >= 1) {
          pairs.push({
            first: requestedMember,
            second: candidate,
            members: [requestedMember, candidate],
            score: matchingFields.length / 3,
            matchingFields
          });
        }
      }
      return pairs;
    }
    for (let index = 0; index < candidates.length; index += 1) {
      for (let nextIndex = index + 1; nextIndex < candidates.length; nextIndex += 1) {
        const first = candidates[index];
        const second = candidates[nextIndex];
        const matchingFields = matchingMemberFields(first, second);
        if (matchingFields.length >= 2 || (matchingFields.length >= 1 && hasExplicitCriteria(criteria))) {
          pairs.push({ first, second, members: [first, second], score: matchingFields.length / 3, matchingFields });
        }
      }
    }
    return pairs;
  }

  async mergeMember(
    treeId: string,
    sourceId: string,
    targetId: string,
    strategy: MergeStrategy = 'nonEmpty',
    actor: MemberMutationActor = undefined
  ): Promise<Member> {
    if (sourceId === targetId) throw new MemberServiceError('INVALID_INPUT', 'Cannot merge a member into itself');
    const members = await getMembers(treeId);
    const source = members.find((member) => member.id === sourceId);
    const target = members.find((member) => member.id === targetId);
    if (!source || !target) throw new MemberServiceError('NOT_FOUND', 'Source or target member not found');

    const preference: 'preferSource' | 'preferTarget' | 'nonEmpty' = resolveMergePreference(strategy);
    const merged = mergeMemberData(target, source, preference);
    const relationships = await getRelationships(treeId);
    const events = await getEvents(treeId);
    const media = await getMediaMetadata(treeId);
    const rewiredRelationships = dedupeRelationships(
      relationships
        .map((relationship) => ({
          ...relationship,
          sourceMemberId: relationship.sourceMemberId === sourceId ? targetId : relationship.sourceMemberId,
          targetMemberId: relationship.targetMemberId === sourceId ? targetId : relationship.targetMemberId
        }))
        .filter((relationship) => relationship.sourceMemberId !== relationship.targetMemberId)
    );
    const updatedEvents = events.map((event) => ({
      ...event,
      memberIds: unique(event.memberIds.map((id) => (id === sourceId ? targetId : id)))
    }));
    const updatedMedia = media.map((item) => {
      if (!mediaMemberIds(item).includes(sourceId)) return item;
      return {
        ...item,
        memberIds: unique(mediaMemberIds(item).map((id) => id === sourceId ? targetId : id)),
        ...(item.memberId === sourceId ? { memberId: targetId } : {})
      };
    });

    const remaining = members.filter((member) => member.id !== sourceId && member.id !== targetId);
    await putMembers(treeId, [...remaining, merged]);
    if (JSON.stringify(rewiredRelationships) !== JSON.stringify(relationships)) {
      await putRelationships(treeId, rewiredRelationships);
    }
    if (JSON.stringify(updatedEvents) !== JSON.stringify(events)) await putEvents(treeId, updatedEvents);
    if (JSON.stringify(updatedMedia) !== JSON.stringify(media)) await putMediaMetadata(treeId, updatedMedia);
    await changeLogService.recordChange({
      treeId,
      userId: actorId(actor),
      memberId: targetId,
      action: 'UPDATE',
      previousData: memberToData(target),
      newData: memberToData(merged),
      fieldChanged: 'merge'
    });
    await changeLogService.recordChange({
      treeId,
      userId: actorId(actor),
      memberId: sourceId,
      action: 'DELETE',
      previousData: memberToData(source),
      fieldChanged: 'merge'
    });
    return merged;
  }
}

export function calculateLifespan(
  dateOfBirth?: string | Pick<Member, 'dateOfBirth' | 'dateOfDeath'>,
  dateOfDeath?: string | Date,
  now: Date = new Date()
): number | null {
  if (typeof dateOfBirth === 'object' && dateOfBirth !== null) {
    if (dateOfDeath instanceof Date) now = dateOfDeath;
    dateOfDeath = dateOfBirth.dateOfDeath;
    dateOfBirth = dateOfBirth.dateOfBirth;
  } else if (dateOfDeath instanceof Date) {
    now = dateOfDeath;
    dateOfDeath = undefined;
  }
  if (!dateOfBirth) return null;
  const birth = parseCalendarDate(dateOfBirth);
  const end = dateOfDeath ? parseCalendarDate(dateOfDeath) : parseCalendarDate(now.toISOString());
  if (!birth || !end || end < birth) return null;
  let years = end.getUTCFullYear() - birth.getUTCFullYear();
  if (
    end.getUTCMonth() < birth.getUTCMonth() ||
    (end.getUTCMonth() === birth.getUTCMonth() && end.getUTCDate() < birth.getUTCDate())
  ) {
    years -= 1;
  }
  return Math.max(0, years);
}

export function getMemberStatus(member: Pick<Member, 'isAlive' | 'dateOfBirth' | 'dateOfDeath'>, now?: Date) {
  const isAlive = !member.dateOfDeath && member.isAlive;
  return {
    isAlive,
    status: isAlive ? 'ALIVE' as const : 'DECEASED' as const,
    lifespan: calculateLifespan(member.dateOfBirth, member.dateOfDeath, now)
  };
}

export function getMemberLifespan(member: Pick<Member, 'dateOfBirth' | 'dateOfDeath'>, now?: Date): number | null {
  return calculateLifespan(member.dateOfBirth, member.dateOfDeath, now);
}

function validateDates(dateOfBirth?: string, dateOfDeath?: string): void {
  const birth = dateOfBirth ? parseCalendarDate(dateOfBirth) : undefined;
  const death = dateOfDeath ? parseCalendarDate(dateOfDeath) : undefined;
  if (dateOfBirth && !birth) throw new MemberServiceError('INVALID_INPUT', 'dateOfBirth must be a valid ISO date');
  if (dateOfDeath && !death) throw new MemberServiceError('INVALID_INPUT', 'dateOfDeath must be a valid ISO date');
  if (birth && death && death < birth) {
    throw new MemberServiceError('INVALID_INPUT', 'dateOfDeath cannot be before dateOfBirth');
  }
}

function validateAvatarMedia(mediaId: string | undefined, media: readonly MediaMetadata[]): void {
  if (!mediaId) return;
  const item = media.find((candidate) => candidate.id === mediaId);
  if (!item) throw new MemberServiceError('INVALID_INPUT', 'Avatar media not found in this family tree');
  if (!item.mimeType.startsWith('image/')) {
    throw new MemberServiceError('INVALID_INPUT', 'Avatar media must be an image');
  }
}

function parseCalendarDate(value: string): Date | undefined {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(value);
  if (!match) return undefined;
  const date = new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3])));
  return date.getUTCFullYear() === Number(match[1]) &&
    date.getUTCMonth() === Number(match[2]) - 1 &&
    date.getUTCDate() === Number(match[3])
    ? date
    : undefined;
}

function actorId(actor: MemberMutationActor): string {
  return typeof actor === 'string' ? actor : actor?.userId ?? 'system';
}

function memberToData(member: Member): Record<string, unknown> {
  return JSON.parse(JSON.stringify(member)) as Record<string, unknown>;
}

function sameJson(a: unknown, b: unknown): boolean {
  return JSON.stringify(a) === JSON.stringify(b);
}

function normalize(value: string | undefined): string {
  return (value ?? '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/gi, 'd')
    .trim()
    .toLocaleLowerCase('vi');
}

function matchingMemberFields(first: Member, second: Member): MemberPair['matchingFields'] {
  const fields: MemberPair['matchingFields'] = [];
  if (normalize(first.fullName) && normalize(first.fullName) === normalize(second.fullName)) fields.push('name');
  if (first.dateOfBirth && second.dateOfBirth && calendarKey(first.dateOfBirth) === calendarKey(second.dateOfBirth)) fields.push('dateOfBirth');
  if (normalize(first.placeOfBirth) && normalize(first.placeOfBirth) === normalize(second.placeOfBirth)) fields.push('placeOfBirth');
  return fields;
}

function matchesCriteria(member: Member, criteria: DuplicateSearchCriteria): boolean {
  const name = criteria.fullName ?? criteria.name;
  if (name && !normalize(member.fullName).includes(normalize(name))) return false;
  if (criteria.firstName && normalize(member.firstName) !== normalize(criteria.firstName)) return false;
  if (criteria.lastName && normalize(member.lastName) !== normalize(criteria.lastName)) return false;
  if (criteria.dateOfBirth && calendarKey(member.dateOfBirth) !== calendarKey(criteria.dateOfBirth)) return false;
  if (criteria.placeOfBirth && normalize(member.placeOfBirth) !== normalize(criteria.placeOfBirth)) return false;
  return true;
}

function hasExplicitCriteria(criteria: DuplicateSearchCriteria): boolean {
  return Object.keys(criteria).some((key) => key !== 'memberId' && criteria[key as keyof DuplicateSearchCriteria] != null);
}

function calendarKey(value?: string): string {
  return value ? value.slice(0, 10) : '';
}

function mergeMemberData(
  target: Member,
  source: Member,
  preference: 'preferSource' | 'preferTarget' | 'nonEmpty'
): Member {
  const merged = { ...target } as Member;
  const fields = Object.keys(target) as Array<keyof Member>;
  for (const field of fields) {
    if (field === 'id' || field === 'treeId' || field === 'createdAt' || field === 'updatedAt') continue;
    const sourceValue = source[field];
    const targetValue = target[field];
    if (preference === 'preferSource' && sourceValue !== undefined && sourceValue !== '') {
      (merged[field] as unknown) = sourceValue;
    } else if (preference === 'nonEmpty' && (targetValue === undefined || targetValue === '') && sourceValue !== undefined) {
      (merged[field] as unknown) = sourceValue;
    }
  }
  merged.updatedAt = new Date().toISOString();
  merged.isAlive = merged.dateOfDeath ? false : merged.isAlive;
  return merged;
}

function resolveMergePreference(strategy: MergeStrategy): 'preferSource' | 'preferTarget' | 'nonEmpty' {
  if (strategy === 'preferSource' || strategy === 'SOURCE_WINS' || strategy === 'PREFER_SOURCE') return 'preferSource';
  if (strategy === 'preferTarget' || strategy === 'TARGET_WINS' || strategy === 'PREFER_TARGET') return 'preferTarget';
  if (strategy === 'nonEmpty') return 'nonEmpty';
  if (strategy.sourceWins || strategy.prefer === 'source') return 'preferSource';
  if (strategy.targetWins || strategy.prefer === 'target') return 'preferTarget';
  return 'nonEmpty';
}

function unique(values: string[]): string[] {
  return [...new Set(values)];
}

function mediaMemberIds(item: MediaMetadata): string[] {
  return unique([...(item.memberIds ?? []), ...(item.memberId ? [item.memberId] : [])]);
}

function mediaEventIds(item: MediaMetadata): string[] {
  return unique([...(item.eventIds ?? []), ...(item.eventId ? [item.eventId] : [])]);
}

function dedupeRelationships(relationships: Relationship[]): Relationship[] {
  const seen = new Set<string>();
  return relationships.filter((relationship) => {
    const key = [
      relationship.sourceMemberId,
      relationship.targetMemberId,
      relationship.type,
      relationship.customType ?? ''
    ].join('|');
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

export const memberService = new MemberService();
export const memberServiceInstance = memberService;
export default memberService;

// Functional exports make the service usable from small server actions while
// retaining the class form for dependency injection and testing.
export const createMember = memberService.createMember.bind(memberService);
export const updateMember = memberService.updateMember.bind(memberService);
export const deleteMember = memberService.deleteMember.bind(memberService);
export const getMemberWithRelations = memberService.getMemberWithRelations.bind(memberService);
export const findDuplicates = memberService.findDuplicates.bind(memberService);
export const mergeMember = memberService.mergeMember.bind(memberService);

// Kept as a type-only reference so accidental schema changes produce a useful
// compile-time error while preserving Zod's detailed errors for API callers.
export type MemberValidationError = ZodError;
