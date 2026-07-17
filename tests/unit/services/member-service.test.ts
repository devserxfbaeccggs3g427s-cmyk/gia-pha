import { describe, expect, it } from 'vitest';
import { getChangeLogs, getEvents, getMediaMetadata, getMembers, getRelationships } from '@/lib/blob/readers';
import { putEvents, putMediaMetadata, putRelationships } from '@/lib/blob/writers';
import { calculateLifespan, MemberService } from '@/lib/services/member-service';

describe('MemberService', () => {
  it('persists complete member data and writes a CREATE audit entry', async () => {
    const service = new MemberService();
    const member = await service.createMember('tree_1', {
      firstName: 'Nguyễn',
      lastName: 'Văn A',
      fullName: 'Nguyễn Văn A',
      gender: 'MALE',
      dateOfBirth: '1980-01-02',
      placeOfBirth: 'Huế',
      occupation: 'Kỹ sư',
      currentAddress: 'Đà Nẵng',
      isAlive: true
    }, 'user_1');

    expect(await getMembers('tree_1')).toEqual([member]);
    await expect(getChangeLogs('tree_1')).resolves.toEqual([
      expect.objectContaining({ action: 'CREATE', userId: 'user_1', memberId: member.id })
    ]);
  });

  it('updates with a snapshot and cascades relationships, events, and media on delete', async () => {
    const service = new MemberService();
    const member = await service.createMember('tree_1', {
      firstName: 'A', lastName: 'B', fullName: 'A B', gender: 'OTHER', isAlive: true
    });
    const other = await service.createMember('tree_1', {
      firstName: 'C', lastName: 'D', fullName: 'C D', gender: 'OTHER', isAlive: true
    });
    await service.updateMember('tree_1', member.id, { biography: 'Updated' }, 'user_2');
    await putRelationships('tree_1', [{
      id: 'rel_1', treeId: 'tree_1', sourceMemberId: member.id, targetMemberId: other.id,
      type: 'SIBLING', createdAt: new Date().toISOString()
    }]);
    await putEvents('tree_1', [{
      id: 'event_1', treeId: 'tree_1', type: 'REUNION', title: 'Family', eventDate: '2024-01-01',
      memberIds: [member.id, other.id], mediaIds: [], createdAt: new Date().toISOString(), updatedAt: new Date().toISOString()
    }]);
    await putMediaMetadata('tree_1', [{
      id: 'media_1', treeId: 'tree_1', memberId: member.id, filename: 'a.webp', originalName: 'a.webp',
      mimeType: 'image/webp', fileSize: 1, blobUrl: 'https://blob.test/a.webp', uploadedAt: new Date().toISOString()
    }]);

    const result = await service.deleteMember('tree_1', member.id, 'user_3');
    expect(result.affectedRelationships).toHaveLength(1);
    expect((await getRelationships('tree_1'))).toEqual([]);
    expect((await getEvents('tree_1'))[0].memberIds).toEqual([other.id]);
    expect(await getMediaMetadata('tree_1')).toEqual([]);
    expect((await getChangeLogs('tree_1')).at(-1)).toEqual(expect.objectContaining({ action: 'DELETE', userId: 'user_3' }));
  });

  it('computes lifespan using the birthday boundary', () => {
    expect(calculateLifespan('2000-06-15', '2020-06-14')).toBe(19);
    expect(calculateLifespan('2000-06-15', '2020-06-15')).toBe(20);
  });
});
