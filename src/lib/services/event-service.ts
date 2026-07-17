import { nanoid } from 'nanoid';
import { createEventSchema, updateEventSchema } from '@/data/schemas';
import type { Event, MediaMetadata, Member } from '@/data/types';
import { getEvents, getMediaMetadata, getMembers } from '@/lib/blob/readers';
import { putEvents, putMediaMetadata } from '@/lib/blob/writers';
import { changeLogService } from './changelog-service';
import { EventServiceError } from './event-media-errors';

export { EventServiceError } from './event-media-errors';

export type EventMutationActor = string | { userId?: string } | undefined;

export interface EventDetails extends Event {
  members: Member[];
  media: MediaMetadata[];
}

export interface UpcomingEvent extends Event {
  nextOccurrence: string;
  daysUntil: number;
}

export class EventService {
  async createEvent(
    treeId: string,
    data: unknown,
    actor: EventMutationActor = undefined
  ): Promise<Event> {
    assertIdentifier(treeId, 'treeId');
    const input = createEventSchema.parse(data);
    const [events, members, media] = await Promise.all([
      getEvents(treeId),
      getMembers(treeId),
      getMediaMetadata(treeId)
    ]);
    validateReferences(input.memberIds, input.mediaIds, members, media);

    const now = new Date().toISOString();
    const event: Event = {
      ...input,
      id: nanoid(),
      treeId,
      createdAt: now,
      updatedAt: now
    };
    const nextEvents = [...events, event];
    const nextMedia = syncMediaEventLinks(media, event.id, [], event.mediaIds);

    await putEvents(treeId, nextEvents);
    try {
      if (!sameJson(media, nextMedia)) await putMediaMetadata(treeId, nextMedia);
    } catch (error) {
      await bestEffort(() => putEvents(treeId, events));
      throw error;
    }
    await changeLogService.recordChange({
      treeId,
      userId: actorId(actor),
      action: 'CREATE',
      entityType: 'EVENT',
      newData: toRecord(event),
      createdAt: now
    });
    return event;
  }

  async updateEvent(
    treeId: string,
    eventId: string,
    data: unknown,
    actor: EventMutationActor = undefined
  ): Promise<Event> {
    assertIdentifier(treeId, 'treeId');
    assertIdentifier(eventId, 'eventId');
    const patch = updateEventSchema.parse(data);
    const [events, members, media] = await Promise.all([
      getEvents(treeId),
      getMembers(treeId),
      getMediaMetadata(treeId)
    ]);
    const index = events.findIndex((event) => event.id === eventId);
    if (index < 0) throw new EventServiceError('NOT_FOUND', 'Event not found');

    const current = events[index];
    // Parsing the merged value enforces cross-field rules such as CUSTOM
    // requiring customType, even when only one of those fields is patched.
    const merged = createEventSchema.parse({ ...current, ...patch });
    validateReferences(merged.memberIds, merged.mediaIds, members, media);
    const next: Event = {
      ...current,
      ...merged,
      id: current.id,
      treeId: current.treeId,
      createdAt: current.createdAt,
      updatedAt: new Date().toISOString()
    };
    if (sameJson(withoutUpdatedAt(current), withoutUpdatedAt(next))) return current;

    const nextEvents = [...events];
    nextEvents[index] = next;
    const nextMedia = syncMediaEventLinks(media, eventId, eventMediaIds(current), next.mediaIds);
    await putEvents(treeId, nextEvents);
    try {
      if (!sameJson(media, nextMedia)) await putMediaMetadata(treeId, nextMedia);
    } catch (error) {
      await bestEffort(() => putEvents(treeId, events));
      throw error;
    }
    await changeLogService.recordChange({
      treeId,
      userId: actorId(actor),
      action: 'UPDATE',
      entityType: 'EVENT',
      previousData: toRecord(current),
      newData: toRecord(next),
      fieldChanged: changedFields(current, next).join(','),
      createdAt: next.updatedAt
    });
    return next;
  }

  async deleteEvent(
    treeId: string,
    eventId: string,
    actor: EventMutationActor = undefined
  ): Promise<Event> {
    assertIdentifier(treeId, 'treeId');
    assertIdentifier(eventId, 'eventId');
    const [events, media] = await Promise.all([getEvents(treeId), getMediaMetadata(treeId)]);
    const event = events.find((candidate) => candidate.id === eventId);
    if (!event) throw new EventServiceError('NOT_FOUND', 'Event not found');

    const nextEvents = events.filter((candidate) => candidate.id !== eventId);
    const nextMedia = syncMediaEventLinks(media, eventId, eventMediaIds(event), []);
    await putEvents(treeId, nextEvents);
    try {
      if (!sameJson(media, nextMedia)) await putMediaMetadata(treeId, nextMedia);
    } catch (error) {
      await bestEffort(() => putEvents(treeId, events));
      throw error;
    }
    await changeLogService.recordChange({
      treeId,
      userId: actorId(actor),
      action: 'DELETE',
      entityType: 'EVENT',
      previousData: toRecord(event)
    });
    return event;
  }

  async getEventsForTree(treeId: string): Promise<Event[]> {
    assertIdentifier(treeId, 'treeId');
    return (await getEvents(treeId)).sort(compareEventDates);
  }

  async getEvent(treeId: string, eventId: string): Promise<Event> {
    assertIdentifier(treeId, 'treeId');
    assertIdentifier(eventId, 'eventId');
    const event = (await getEvents(treeId)).find((candidate) => candidate.id === eventId);
    if (!event) throw new EventServiceError('NOT_FOUND', 'Event not found');
    return event;
  }

  async getEventWithRelations(treeId: string, eventId: string): Promise<EventDetails> {
    const [event, members, media] = await Promise.all([
      this.getEvent(treeId, eventId),
      getMembers(treeId),
      getMediaMetadata(treeId)
    ]);
    const memberIds = new Set(event.memberIds);
    const mediaIds = new Set(eventMediaIds(event));
    return {
      ...event,
      members: members.filter((member) => memberIds.has(member.id)),
      media: media.filter((item) => mediaIds.has(item.id) || mediaEventIds(item).includes(event.id))
    };
  }

  async getUpcomingEvents(
    treeId: string,
    referenceDate: Date = new Date(),
    days = 7
  ): Promise<UpcomingEvent[]> {
    assertIdentifier(treeId, 'treeId');
    if (!Number.isInteger(days) || days < 0 || days > 366) {
      throw new EventServiceError('INVALID_INPUT', 'days must be an integer between 0 and 366');
    }
    if (Number.isNaN(referenceDate.getTime())) {
      throw new EventServiceError('INVALID_INPUT', 'referenceDate must be valid');
    }

    const today = utcCalendarDate(referenceDate);
    const limit = addUtcDays(today, days);
    const upcoming: UpcomingEvent[] = [];
    for (const event of await getEvents(treeId)) {
      const sourceDate = parseCalendarDate(event.eventDate);
      const occurrence = isAnnualEvent(event)
        ? nextAnnualOccurrence(sourceDate, today)
        : sourceDate;
      if (occurrence < today || occurrence > limit) continue;
      upcoming.push({
        ...event,
        nextOccurrence: occurrence.toISOString().slice(0, 10),
        daysUntil: differenceInUtcDays(today, occurrence)
      });
    }
    return upcoming.sort((a, b) => a.daysUntil - b.daysUntil || a.title.localeCompare(b.title));
  }
}

function validateReferences(
  memberIds: string[],
  mediaIds: string[],
  members: Array<{ id: string }>,
  media: Array<{ id: string }>
): void {
  const knownMembers = new Set(members.map((member) => member.id));
  const knownMedia = new Set(media.map((item) => item.id));
  const missingMembers = memberIds.filter((id) => !knownMembers.has(id));
  const missingMedia = mediaIds.filter((id) => !knownMedia.has(id));
  if (missingMembers.length || missingMedia.length) {
    const messages = [
      ...(missingMembers.length ? [`Members not found: ${missingMembers.join(', ')}`] : []),
      ...(missingMedia.length ? [`Media not found: ${missingMedia.join(', ')}`] : [])
    ];
    throw new EventServiceError('INVALID_INPUT', messages.join('; '));
  }
}

function syncMediaEventLinks(
  media: MediaMetadata[],
  eventId: string,
  previousMediaIds: string[],
  nextMediaIds: string[]
): MediaMetadata[] {
  const previous = new Set(previousMediaIds);
  const next = new Set(nextMediaIds);
  return media.map((item) => {
    if (!previous.has(item.id) && !next.has(item.id) && !mediaEventIds(item).includes(eventId)) return item;
    const eventIds = new Set(mediaEventIds(item));
    if (next.has(item.id)) eventIds.add(eventId);
    else eventIds.delete(eventId);
    const normalized = [...eventIds];
    return {
      ...item,
      eventIds: normalized,
      ...(item.eventId === eventId && !eventIds.has(eventId) ? { eventId: undefined } : {})
    };
  });
}

function mediaEventIds(item: MediaMetadata): string[] {
  return [...new Set([...(item.eventIds ?? []), ...(item.eventId ? [item.eventId] : [])])];
}

function eventMediaIds(event: Event): string[] {
  return [...new Set(event.mediaIds ?? [])];
}

function isAnnualEvent(event: Event): boolean {
  return event.type === 'BIRTHDAY' || event.type === 'ANNIVERSARY';
}

function parseCalendarDate(value: string): Date {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(value);
  if (!match) throw new EventServiceError('INVALID_INPUT', `Invalid eventDate: ${value}`);
  return new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3])));
}

function utcCalendarDate(value: Date): Date {
  return new Date(Date.UTC(value.getUTCFullYear(), value.getUTCMonth(), value.getUTCDate()));
}

function nextAnnualOccurrence(source: Date, today: Date): Date {
  let year = today.getUTCFullYear();
  let occurrence = annualDate(year, source.getUTCMonth(), source.getUTCDate());
  if (occurrence < today) occurrence = annualDate(++year, source.getUTCMonth(), source.getUTCDate());
  return occurrence;
}

function annualDate(year: number, month: number, day: number): Date {
  // Feb 29 reminders use Feb 28 in non-leap years, a predictable convention
  // that avoids silently skipping an entire year.
  if (month === 1 && day === 29 && new Date(Date.UTC(year, 1, 29)).getUTCMonth() !== 1) {
    return new Date(Date.UTC(year, 1, 28));
  }
  return new Date(Date.UTC(year, month, day));
}

function addUtcDays(value: Date, days: number): Date {
  return new Date(value.getTime() + days * 86_400_000);
}

function differenceInUtcDays(from: Date, to: Date): number {
  return Math.round((to.getTime() - from.getTime()) / 86_400_000);
}

function compareEventDates(a: Event, b: Event): number {
  return a.eventDate.localeCompare(b.eventDate) || a.title.localeCompare(b.title);
}

function changedFields(previous: Event, next: Event): string[] {
  return Object.keys(previous).filter((key) => key !== 'updatedAt' && !sameJson(
    (previous as unknown as Record<string, unknown>)[key],
    (next as unknown as Record<string, unknown>)[key]
  ));
}

function withoutUpdatedAt(event: Event): Omit<Event, 'updatedAt'> {
  const { updatedAt: _updatedAt, ...rest } = event;
  return rest;
}

function assertIdentifier(value: string, name: string): void {
  if (!value?.trim()) throw new EventServiceError('INVALID_INPUT', `${name} is required`);
}

function actorId(actor: EventMutationActor): string {
  return typeof actor === 'string' ? actor : actor?.userId ?? 'system';
}

function toRecord(value: Event): Record<string, unknown> {
  return value as unknown as Record<string, unknown>;
}

function sameJson(left: unknown, right: unknown): boolean {
  return JSON.stringify(left) === JSON.stringify(right);
}

async function bestEffort(operation: () => Promise<void>): Promise<void> {
  try {
    await operation();
  } catch {
    // Preserve the original error. Blob writes are last-write-wins and a
    // failed compensation is still more actionable through that first error.
  }
}

export const eventService = new EventService();
export default eventService;

export const createEvent = eventService.createEvent.bind(eventService);
export const updateEvent = eventService.updateEvent.bind(eventService);
export const deleteEvent = eventService.deleteEvent.bind(eventService);
export const getEventsForTree = eventService.getEventsForTree.bind(eventService);
export const getUpcomingEvents = eventService.getUpcomingEvents.bind(eventService);
