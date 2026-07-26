import 'server-only';

import { nanoid } from 'nanoid';
import { createEventSchema, updateEventSchema } from '@/data/schemas';
import type { Event, MediaMetadata, Member } from '@/data/types';
import { ServiceError } from '@/lib/api/service-error';
import { requireBridgeCookie } from '@/lib/api/spring-guard';
import { springFetch } from '@/lib/api/spring-client';
import {
    fromSpringEvent,
    fromSpringMedia,
    fromSpringMember,
    toSpringEvent,
    type SpringEventDto,
    type SpringMediaDto,
    type SpringMemberDto
} from '@/lib/api/dto-mapper';
import { mediaMemberIds } from '@/lib/services/_helpers/member-helpers';

export type EventMutationActor = string | { userId?: string } | undefined;

export interface EventDetails extends Event {
    members: Member[];
    media: MediaMetadata[];
}

export interface UpcomingEvent extends Event {
    nextOccurrence: string;
    daysUntil: number;
}

export class EventServiceError extends ServiceError {
    constructor(code: 'NOT_FOUND' | 'INVALID_INPUT' | 'CONFLICT', message: string) {
        super(code, message);
        this.name = 'EventServiceError';
    }
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

async function fetchMembersList(treeId: string, cookie: string): Promise<Member[]> {
    const res = await springFetch<SpringMemberDto[] | { data?: SpringMemberDto[] }>(
        `/api/trees/${encodeURIComponent(treeId)}/members`,
        { method: 'GET', public: true },
        cookie
    );
    const list = Array.isArray(res.body) ? res.body : res.body?.data ?? [];
    return list.map(fromSpringMember);
}

export class EventService {
    async createEvent(
        treeId: string,
        data: unknown,
        actor: EventMutationActor = undefined
    ): Promise<Event> {
        assertIdentifier(treeId, 'treeId');
        const input = createEventSchema.parse(data);
        const cookie = await requireBridgeCookie();
        const now = new Date().toISOString();
        const event: Event = {
            ...input,
            id: nanoid(),
            treeId,
            createdAt: now,
            updatedAt: now
        };
        const res = await springFetch<SpringEventDto>(
            `/api/trees/${encodeURIComponent(treeId)}/events`,
            { method: 'POST', body: toSpringEvent(event) },
            cookie
        );
        return fromSpringEvent(res.body);
    }

    async updateEvent(
        treeId: string,
        eventId: string,
        data: unknown,
        actor: EventMutationActor = undefined
    ): Promise<Event> {
        assertIdentifier(treeId, 'treeId');
        assertIdentifier(eventId, 'eventId');
        const input = updateEventSchema.parse(data);
        const cookie = await requireBridgeCookie();
        const res = await springFetch<SpringEventDto>(
            `/api/trees/${encodeURIComponent(treeId)}/events/${encodeURIComponent(eventId)}`,
            { method: 'PATCH', body: toSpringEvent(input) },
            cookie
        );
        return fromSpringEvent(res.body);
    }

    async deleteEvent(
        treeId: string,
        eventId: string,
        actor: EventMutationActor = undefined
    ): Promise<void> {
        assertIdentifier(treeId, 'treeId');
        assertIdentifier(eventId, 'eventId');
        const cookie = await requireBridgeCookie();
        await springFetch<unknown>(
            `/api/trees/${encodeURIComponent(treeId)}/events/${encodeURIComponent(eventId)}`,
            { method: 'DELETE' },
            cookie
        );
    }

    async getEventsForTree(treeId: string): Promise<Event[]> {
        const cookie = await requireBridgeCookie();
        return fetchEventsList(treeId, cookie);
    }

    async getEvent(treeId: string, eventId: string): Promise<Event> {
        const cookie = await requireBridgeCookie();
        const res = await springFetch<SpringEventDto>(
            `/api/trees/${encodeURIComponent(treeId)}/events/${encodeURIComponent(eventId)}`,
            { method: 'GET' },
            cookie
        );
        return fromSpringEvent(res.body);
    }

    async getEventWithRelations(treeId: string, eventId: string): Promise<EventDetails> {
        const cookie = await requireBridgeCookie();
        const [event, members, media] = await Promise.all([
            this.getEvent(treeId, eventId),
            fetchMembersList(treeId, cookie),
            fetchMediaList(treeId, cookie),
        ]);
        const eventMemberIds = new Set(event.memberIds);
        const eventMediaIds = new Set(event.mediaIds);
        return {
            ...event,
            members: members.filter((m) => eventMemberIds.has(m.id)),
            media: media.filter((item) => eventMediaIds.has(item.id) || mediaMemberIds(item).some((id) => eventMemberIds.has(id))),
        };
    }

    async getUpcomingEvents(treeId: string, options: { limit?: number; now?: Date } = {}): Promise<UpcomingEvent[]> {
        const cookie = await requireBridgeCookie();
        const events = await fetchEventsList(treeId, cookie);
        const now = options.now ?? new Date();
        const windowMs = 366 * 24 * 60 * 60 * 1000;
        const out: UpcomingEvent[] = [];
        for (const event of events) {
            const nextOccurrence = computeNextOccurrence(event, now);
            if (!nextOccurrence) continue;
            const daysUntil = Math.floor((nextOccurrence.getTime() - now.getTime()) / (24 * 60 * 60 * 1000));
            if (daysUntil < 0 || daysUntil > 366) continue;
            out.push({ ...event, nextOccurrence: nextOccurrence.toISOString(), daysUntil });
            if (options.limit && out.length >= options.limit) break;
        }
        out.sort((a, b) => a.daysUntil - b.daysUntil);
        return out;
    }
}

function assertIdentifier(value: string, name: string): void {
    if (!value || typeof value !== 'string') {
        throw new EventServiceError('INVALID_INPUT', `${name} is required`);
    }
}

function computeNextOccurrence(event: Event, now: Date): Date | undefined {
    const base = new Date(event.eventDate);
    if (Number.isNaN(base.getTime())) return undefined;
    if (base >= now) return base;
    if ((event as { recurrence?: string }).recurrence !== 'YEARLY') return undefined;
    const candidate = new Date(now.getFullYear(), base.getMonth(), base.getDate());
    if (candidate < now) candidate.setFullYear(candidate.getFullYear() + 1);
    if (base.getMonth() === 1 && base.getDate() === 29 && candidate.getMonth() === 2) {
        candidate.setDate(28);
    }
    return candidate;
}

export const eventService = new EventService();
export default eventService;
