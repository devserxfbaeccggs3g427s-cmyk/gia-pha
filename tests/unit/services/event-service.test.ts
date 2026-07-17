import { describe, expect, it } from 'vitest';
import { getEvents, getMediaMetadata } from '@/lib/blob/readers';
import { putMediaMetadata } from '@/lib/blob/writers';
import { EventService, EventServiceError } from '@/lib/services/event-service';
import { buildEvent, buildMediaMetadata, buildMember } from '../../utils/factories';
import { putMembers } from '@/lib/blob/writers';

describe('EventService', () => {
  it('creates, updates and deletes events while maintaining media links', async () => {
    const service = new EventService();
    const member = buildMember({ id: 'member-1', treeId: 'tree-1' });
    const media = buildMediaMetadata({ id: 'media-1', treeId: 'tree-1' });
    await putMembers('tree-1', [member]);
    await putMediaMetadata('tree-1', [media]);

    const created = await service.createEvent('tree-1', {
      type: 'REUNION',
      title: 'Họp mặt gia đình',
      eventDate: '2026-08-01',
      memberIds: [member.id],
      mediaIds: [media.id]
    }, 'user-1');
    expect(created).toMatchObject({ memberIds: [member.id], mediaIds: [media.id] });
    expect((await getMediaMetadata('tree-1'))[0].eventIds).toEqual([created.id]);

    const updated = await service.updateEvent('tree-1', created.id, {
      title: 'Họp mặt lớn',
      mediaIds: []
    });
    expect(updated.title).toBe('Họp mặt lớn');
    expect((await getMediaMetadata('tree-1'))[0].eventIds).toEqual([]);

    await service.deleteEvent('tree-1', created.id, 'user-1');
    await expect(getEvents('tree-1')).resolves.toEqual([]);
  });

  it('validates member and media references and supports annual reminders', async () => {
    const service = new EventService();
    await expect(service.createEvent('tree-1', {
      type: 'REUNION', title: 'Invalid', eventDate: '2026-01-01', memberIds: ['missing']
    })).rejects.toMatchObject({ code: 'INVALID_INPUT' } satisfies Partial<EventServiceError>);

    await service.createEvent('tree-1', {
      type: 'BIRTHDAY', title: 'Birthday', eventDate: '2000-07-22', memberIds: [], mediaIds: []
    });
    await service.createEvent('tree-1', {
      type: 'FUNERAL', title: 'Old funeral', eventDate: '2026-07-25', memberIds: [], mediaIds: []
    });
    const upcoming = await service.getUpcomingEvents('tree-1', new Date('2026-07-18T10:00:00.000Z'));
    expect(upcoming.map((event) => event.title)).toEqual(['Birthday', 'Old funeral']);
    expect(upcoming[0]).toMatchObject({ nextOccurrence: '2026-07-22', daysUntil: 4 });
  });

  it('returns events in chronological order', async () => {
    const service = new EventService();
    const events = [
      buildEvent({ id: 'late', treeId: 'tree-1', eventDate: '2027-01-01', title: 'B' }),
      buildEvent({ id: 'early', treeId: 'tree-1', eventDate: '2026-01-01', title: 'A' })
    ];
    const { putEvents } = await import('@/lib/blob/writers');
    await putEvents('tree-1', events);
    await expect(service.getEventsForTree('tree-1')).resolves.toEqual([events[1], events[0]]);
  });
});
