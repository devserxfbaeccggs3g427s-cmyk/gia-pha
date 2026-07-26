import type { MediaMetadata, Member, Relationship } from '@/data/types';

/**
 * Re-exported types used by both {@link MemberService} and the helpers
 * below. Keeping them here avoids a circular import between
 * member-service.ts and member-helpers.ts.
 */
export interface MemberPair {
    first: Member;
    second: Member;
    members: [Member, Member];
    score: number;
    matchingFields: string[];
}

export interface DuplicateSearchCriteria {
    memberId?: string;
    firstName?: string;
    lastName?: string;
    nickname?: string;
    dateOfBirth?: string;
    phone?: string;
    email?: string;
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

// --- helpers extracted from the legacy Blob-backed implementation ---

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
  }

  if (!dateOfBirth) return null;
  const birth = parseCalendarDate(dateOfBirth);
  if (!birth) return null;

  const reference = dateOfDeath && typeof dateOfDeath === 'string' ? parseCalendarDate(dateOfDeath) ?? now : now;
  const diff = reference.getTime() - birth.getTime();
  if (Number.isNaN(diff)) return null;
  return Math.max(0, Math.floor(diff / (1000 * 60 * 60 * 24 * 365.25)));
}

export function getMemberStatus(
  member: Pick<Member, 'isAlive' | 'dateOfBirth' | 'dateOfDeath'>,
  now: Date = new Date()
): { status: 'ALIVE' | 'DECEASED'; lifespan: number | null } {
  if (!member.isAlive || member.dateOfDeath) {
    return { status: 'DECEASED', lifespan: calculateLifespan(member, undefined, now) };
  }
  return { status: 'ALIVE', lifespan: calculateLifespan(member, undefined, now) };
}

export function getMemberLifespan(
  member: Pick<Member, 'dateOfBirth' | 'dateOfDeath'>,
  now: Date = new Date()
): number | null {
  return calculateLifespan(member, undefined, now);
}

export function validateDates(dateOfBirth?: string, dateOfDeath?: string): void {
  if (!dateOfBirth || !dateOfDeath) return;
  const birth = parseCalendarDate(dateOfBirth);
  const death = parseCalendarDate(dateOfDeath);
  if (birth && death && death < birth) {
    throw new Error('dateOfDeath cannot be before dateOfBirth');
  }
}

export function validateAvatarMedia(
  mediaId: string | undefined,
  media: readonly MediaMetadata[]
): void {
  if (!mediaId) return;
  const found = media.some((item) => item.id === mediaId);
  if (!found) throw new Error(`Avatar media "${mediaId}" not found in tree`);
}

export function parseCalendarDate(value: string): Date | undefined {
  if (!value) return undefined;
  const trimmed = value.trim();
  if (!trimmed) return undefined;
  const direct = new Date(trimmed);
  if (!Number.isNaN(direct.getTime())) return direct;
  const match = trimmed.match(/^(\d{4})-(\d{2})-(\d{2})/);
  if (!match) return undefined;
  const [, yearStr, monthStr, dayStr] = match;
  const year = Number(yearStr);
  const month = Number(monthStr);
  const day = Number(dayStr);
  if (!year || !month || !day) return undefined;
  return new Date(Date.UTC(year, month - 1, day));
}

export function actorId(actor: unknown): string {
  if (typeof actor === 'string') return actor;
  if (actor && typeof actor === 'object' && 'userId' in actor) {
    return (actor as { userId?: string }).userId ?? 'system';
  }
  return 'system';
}

export function memberToData(member: Member): Record<string, unknown> {
  const data: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(member)) {
    data[key] = value;
  }
  return data;
}

export function sameJson(a: unknown, b: unknown): boolean {
  return JSON.stringify(a ?? null) === JSON.stringify(b ?? null);
}

export function normalize(value: string | undefined): string {
  return (value ?? '').trim().toLowerCase().replace(/\s+/g, ' ');
}

export function matchingMemberFields(first: Member, second: Member): MemberPair['matchingFields'] {
  const fields: MemberPair['matchingFields'] = [];
  if (normalize(first.firstName) && normalize(first.firstName) === normalize(second.firstName)) fields.push('firstName');
  if (normalize(first.lastName) && normalize(first.lastName) === normalize(second.lastName)) fields.push('lastName');
  if (calendarKey(first.dateOfBirth) && calendarKey(first.dateOfBirth) === calendarKey(second.dateOfBirth))
    fields.push('dateOfBirth');
  return fields;
}

export function matchesCriteria(member: Member, criteria: DuplicateSearchCriteria): boolean {
  if (criteria.firstName && normalize(criteria.firstName) !== normalize(member.firstName)) return false;
  if (criteria.lastName && normalize(criteria.lastName) !== normalize(member.lastName)) return false;
  if (criteria.nickname && normalize(criteria.nickname) !== normalize(member.nickname)) return false;
  if (criteria.dateOfBirth && calendarKey(criteria.dateOfBirth) !== calendarKey(member.dateOfBirth))
    return false;
  if (criteria.phone && normalize(criteria.phone) !== normalize(member.phone)) return false;
  if (criteria.email && normalize(criteria.email) !== normalize(member.email)) return false;
  return true;
}

export function hasExplicitCriteria(criteria: DuplicateSearchCriteria): boolean {
  return Object.keys(criteria).some(
    (key) => key !== 'memberId' && criteria[key as keyof DuplicateSearchCriteria] != null
  );
}

export function calendarKey(value?: string): string {
  if (!value) return '';
  return value.slice(0, 10);
}

export function mergeMemberData(
  target: Member,
  source: Member,
  preference: 'preferSource' | 'preferTarget' | 'nonEmpty'
): Member {
  const merged: Record<string, unknown> = { ...target };
  for (const key of Object.keys(source)) {
    if (key === 'id' || key === 'treeId' || key === 'createdAt') continue;
    const targetValue = merged[key];
    const sourceValue = (source as unknown as Record<string, unknown>)[key];
    if (preference === 'preferSource') {
      if (sourceValue !== undefined && sourceValue !== null && sourceValue !== '') {
        merged[key] = sourceValue;
      }
    } else if (preference === 'preferTarget') {
      // keep target as-is
    } else {
      if ((targetValue === undefined || targetValue === null || targetValue === '') &&
          sourceValue !== undefined && sourceValue !== null && sourceValue !== '') {
        merged[key] = sourceValue;
      }
    }
  }
  (merged as unknown as Member).updatedAt = new Date().toISOString();
  return merged as unknown as Member;
}

export function resolveMergePreference(
  strategy: MergeStrategy
): 'preferSource' | 'preferTarget' | 'nonEmpty' {
  if (strategy === 'preferSource' || strategy === 'SOURCE_WINS' || strategy === 'PREFER_SOURCE') return 'preferSource';
  if (strategy === 'preferTarget' || strategy === 'TARGET_WINS' || strategy === 'PREFER_TARGET') return 'preferTarget';
  if (typeof strategy === 'object') {
    if (strategy.prefer === 'source') return 'preferSource';
    if (strategy.prefer === 'target') return 'preferTarget';
    if (strategy.sourceWins) return 'preferSource';
    if (strategy.targetWins) return 'preferTarget';
  }
  return 'nonEmpty';
}

export function unique(values: string[]): string[] {
  return Array.from(new Set(values));
}

export function mediaMemberIds(item: MediaMetadata): string[] {
  if (item.memberIds && item.memberIds.length > 0) return item.memberIds;
  return item.memberId ? [item.memberId] : [];
}

export function mediaEventIds(item: MediaMetadata): string[] {
  if (item.eventIds && item.eventIds.length > 0) return item.eventIds;
  return item.eventId ? [item.eventId] : [];
}

export function dedupeRelationships(relationships: Relationship[]): Relationship[] {
  const seen = new Set<string>();
  const out: Relationship[] = [];
  for (const r of relationships) {
    const key = `${r.sourceMemberId}::${r.targetMemberId}::${r.type}`;
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(r);
  }
  return out;
}
