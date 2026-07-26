import 'server-only';

import { nanoid } from 'nanoid';
import { createMemberSchema, updateMemberSchema } from '@/data/schemas';
import type {
    Event,
    MediaMetadata,
    Member,
    Relationship
} from '@/data/types';
import { ServiceError } from '@/lib/api/service-error';
import { requireBridgeCookie } from '@/lib/api/spring-guard';
import { springFetch } from '@/lib/api/spring-client';
import {
    fromSpringMember,
    fromSpringRelationship,
    fromSpringEvent,
    fromSpringMedia,
    toSpringMember,
    type SpringMemberDto,
    type SpringRelationshipDto,
    type SpringEventDto,
    type SpringMediaDto
} from '@/lib/api/dto-mapper';
import {
    calculateLifespan as calculateLifespanHelper,
    getMemberStatus as getMemberStatusHelper,
    matchingMemberFields,
    mergeMemberData,
    dedupeRelationships,
    resolveMergePreference,
    mediaMemberIds,
    mediaEventIds,
    memberToData,
    validateDates as validateDatesHelper,
    validateAvatarMedia as validateAvatarMediaHelper,
    matchesCriteria,
    hasExplicitCriteria,
    unique,
    sameJson
} from '@/lib/services/_helpers/member-helpers';

export type MemberMutationActor = string | { userId?: string } | undefined;

export interface MemberFull extends Member {
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
    deletedRelationships: Relationship[];
    affectedEvents: Event[];
    deletedMedia: MediaMetadata[];
}

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

export class MemberServiceError extends ServiceError {
    constructor(code: 'NOT_FOUND' | 'INVALID_INPUT' | 'CONFLICT', message: string) {
        super(code, message);
        this.name = 'MemberServiceError';
    }
}

async function fetchMembersList(treeId: string, cookie: string): Promise<Member[]> {
    const res = await springFetch<SpringMemberDto[] | { data?: SpringMemberDto[] }>(
        `/api/trees/${encodeURIComponent(treeId)}/members`,
        { method: 'GET', public: true },
        cookie
    );
    const list = Array.isArray(res.body) ? res.body : res.body?.data ?? [];
    return list.map(fromSpringMember);
}

async function fetchRelationshipsList(treeId: string, cookie: string): Promise<Relationship[]> {
    const res = await springFetch<SpringRelationshipDto[] | { data?: SpringRelationshipDto[] }>(
        `/api/trees/${encodeURIComponent(treeId)}/relationships`,
        { method: 'GET', public: true },
        cookie
    );
    const list = Array.isArray(res.body) ? res.body : res.body?.data ?? [];
    return list.map(fromSpringRelationship);
}

async function fetchEventsList(treeId: string, cookie: string): Promise<Event[]> {
    const res = await springFetch<SpringEventDto[] | { data?: SpringEventDto[] }>(
        `/api/trees/${encodeURIComponent(treeId)}/events`,
        { method: 'GET', public: true },
        cookie
    );
    const list = Array.isArray(res.body) ? res.body : res.body?.data ?? [];
    return list.map(fromSpringEvent);
}

async function fetchMediaList(treeId: string, cookie: string): Promise<MediaMetadata[]> {
    const res = await springFetch<SpringMediaDto[] | { data?: SpringMediaDto[] }>(
        `/api/trees/${encodeURIComponent(treeId)}/media`,
        { method: 'GET', public: true },
        cookie
    );
    const list = Array.isArray(res.body) ? res.body : res.body?.data ?? [];
    return list.map(fromSpringMedia);
}

export class MemberService {
    calculateLifespan(
        dateOfBirth?: string | Pick<Member, 'dateOfBirth' | 'dateOfDeath'>,
        dateOfDeath?: string | Date,
        now?: Date
    ): number | null {
        return calculateLifespanHelper(dateOfBirth, dateOfDeath, now);
    }

    getMemberStatus(member: Pick<Member, 'isAlive' | 'dateOfBirth' | 'dateOfDeath'>, now?: Date) {
        return getMemberStatusHelper(member, now);
    }

    async createMember(
        treeId: string,
        data: unknown,
        actor: MemberMutationActor = undefined
    ): Promise<Member> {
        const input = createMemberSchema.parse(data);
        validateDatesHelper(input.dateOfBirth, input.dateOfDeath);
        const cookie = await requireBridgeCookie();
        const media = await fetchMediaList(treeId, cookie);
        validateAvatarMediaHelper(input.avatarMediaId, media);
        const now = new Date().toISOString();
        const member: Member = {
            ...input,
            id: nanoid(),
            treeId,
            isAlive: input.dateOfDeath ? false : input.isAlive,
            createdAt: now,
            updatedAt: now
        };

        const res = await springFetch<SpringMemberDto>(
            `/api/trees/${encodeURIComponent(treeId)}/members`,
            { method: 'POST', body: toSpringMember(member) },
            cookie
        );
        return fromSpringMember(res.body);
    }

    async updateMember(
        treeId: string,
        memberId: string,
        data: unknown,
        actor: MemberMutationActor = undefined
    ): Promise<Member> {
        const input = updateMemberSchema.parse(data);
        const cookie = await requireBridgeCookie();
        const [members, media] = await Promise.all([
            fetchMembersList(treeId, cookie),
            fetchMediaList(treeId, cookie),
        ]);
        const current = members.find((m) => m.id === memberId);
        if (!current) throw new MemberServiceError('NOT_FOUND', 'Member not found');
        validateAvatarMediaHelper(input.avatarMediaId, media);

        const nextDateOfBirth = input.dateOfBirth ?? current.dateOfBirth;
        const nextDateOfDeath = input.dateOfDeath ?? current.dateOfDeath;
        validateDatesHelper(nextDateOfBirth, nextDateOfDeath);
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

        const res = await springFetch<SpringMemberDto>(
            `/api/trees/${encodeURIComponent(treeId)}/members/${encodeURIComponent(memberId)}`,
            { method: 'PATCH', body: toSpringMember(next) },
            cookie
        );
        return fromSpringMember(res.body);
    }

    async deleteMember(
        treeId: string,
        memberId: string,
        actor: MemberMutationActor = undefined
    ): Promise<DeleteMemberResult> {
        const cookie = await requireBridgeCookie();
        const [members, relationships, events, media] = await Promise.all([
            fetchMembersList(treeId, cookie),
            fetchRelationshipsList(treeId, cookie),
            fetchEventsList(treeId, cookie),
            fetchMediaList(treeId, cookie),
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

        await springFetch<unknown>(
            `/api/trees/${encodeURIComponent(treeId)}/members/${encodeURIComponent(memberId)}`,
            { method: 'DELETE' },
            cookie
        );

        return {
            member,
            affectedRelationships,
            deletedRelationships: affectedRelationships,
            affectedEvents,
            deletedMedia,
        };
    }

    async listMembers(treeId: string): Promise<Member[]> {
        const cookie = await requireBridgeCookie();
        return fetchMembersList(treeId, cookie);
    }

    async getMember(treeId: string, memberId: string): Promise<Member> {
        const cookie = await requireBridgeCookie();
        const res = await springFetch<SpringMemberDto>(
            `/api/trees/${encodeURIComponent(treeId)}/members/${encodeURIComponent(memberId)}`,
            { method: 'GET' },
            cookie
        );
        return fromSpringMember(res.body);
    }

    async getMemberWithRelations(treeId: string, memberId: string): Promise<MemberFull> {
        const cookie = await requireBridgeCookie();
        const [members, relationships, events, media] = await Promise.all([
            fetchMembersList(treeId, cookie),
            fetchRelationshipsList(treeId, cookie),
            fetchEventsList(treeId, cookie),
            fetchMediaList(treeId, cookie),
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
            ...getMemberStatusHelper(member)
        };
    }

    async findDuplicates(treeId: string, criteria: DuplicateSearchCriteria = {}): Promise<MemberPair[]> {
        const cookie = await requireBridgeCookie();
        const members = await fetchMembersList(treeId, cookie);
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
        const cookie = await requireBridgeCookie();
        const [members, relationships, events, media] = await Promise.all([
            fetchMembersList(treeId, cookie),
            fetchRelationshipsList(treeId, cookie),
            fetchEventsList(treeId, cookie),
            fetchMediaList(treeId, cookie),
        ]);
        const source = members.find((member) => member.id === sourceId);
        const target = members.find((member) => member.id === targetId);
        if (!source || !target) throw new MemberServiceError('NOT_FOUND', 'Source or target member not found');

        const preference: 'preferSource' | 'preferTarget' | 'nonEmpty' = resolveMergePreference(strategy);
        const merged = mergeMemberData(target, source, preference);
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

        // Apply merge by: PATCH target with merged data, then DELETE source.
        const mergedRes = await springFetch<SpringMemberDto>(
            `/api/trees/${encodeURIComponent(treeId)}/members/${encodeURIComponent(targetId)}`,
            { method: 'PATCH', body: toSpringMember(merged) },
            cookie
        );
        await springFetch<unknown>(
            `/api/trees/${encodeURIComponent(treeId)}/members/${encodeURIComponent(sourceId)}`,
            { method: 'DELETE' },
            cookie
        );
        return fromSpringMember(mergedRes.body);
    }
}

export const memberService = new MemberService();
export default memberService;

export const createMember = memberService.createMember.bind(memberService);
export const updateMember = memberService.updateMember.bind(memberService);
export const deleteMember = memberService.deleteMember.bind(memberService);
export const getMemberWithRelations = memberService.getMemberWithRelations.bind(memberService);
export const findDuplicates = memberService.findDuplicates.bind(memberService);
export const mergeMember = memberService.mergeMember.bind(memberService);
export { memberToData };
