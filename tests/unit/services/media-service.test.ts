import { describe, expect, it } from 'vitest';
import { getAlbums, getEvents, getMediaMetadata, getMembers } from '@/lib/blob/readers';
import { MediaService, MediaServiceError } from '@/lib/services/media-service';
import { putEvents, putMembers } from '@/lib/blob/writers';
import { mockBlobStorage } from '../../utils/mock-blob-storage';
import { buildEvent, buildMember } from '../../utils/factories';

const onePixelPng = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
  'base64'
);

function uploadFile(bytes: Buffer = onePixelPng, type = 'image/png', name = 'family.png') {
  return {
    name,
    type,
    size: bytes.length,
    arrayBuffer: async () => bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer
  };
}

describe('MediaService', () => {
  it('uploads original and real WebP thumbnail, stores multi-links, and supports albums', async () => {
    const service = new MediaService();
    const member = buildMember({ id: 'member-1', treeId: 'tree-1' });
    const event = buildEvent({ id: 'event-1', treeId: 'tree-1', mediaIds: [] });
    await putMembers('tree-1', [member]);
    await putEvents('tree-1', [event]);
    const album = await service.createAlbum('tree-1', { title: 'Kỷ niệm 2026' });

    const item = await service.uploadMedia('tree-1', uploadFile(), {
      memberId: member.id, eventIds: [event.id], albumId: album.id, isAvatar: true
    }, 'user-1');
    expect(item).toMatchObject({
      mimeType: 'image/png',
      memberIds: [member.id],
      eventIds: [event.id],
      albumId: album.id,
      thumbnailUrl: expect.any(String),
      contentUrl: expect.stringContaining('/api/media/')
    });
    expect(mockBlobStorage.list('media/tree-1/')).toHaveLength(2);
    expect((await getEvents('tree-1'))[0].mediaIds).toEqual([item.id]);
    expect((await getMembers('tree-1'))[0].avatarMediaId).toBe(item.id);
    await expect(service.getMediaForMember('tree-1', member.id)).resolves.toEqual([item]);
    await expect(service.getMediaForEvent('tree-1', event.id)).resolves.toEqual([item]);
    await expect(service.getAlbums('tree-1')).resolves.toEqual([album]);

    await service.deleteMedia('tree-1', item.id, 'user-1');
    await expect(getMediaMetadata('tree-1')).resolves.toEqual([]);
    const memberAfterDelete = (await getMembers('tree-1'))[0];
    expect(memberAfterDelete.id).toBe(member.id);
    expect(memberAfterDelete).not.toHaveProperty('avatarMediaId');
    expect(mockBlobStorage.list('media/tree-1/')).toHaveLength(0);
  });

  it('rejects oversized, unsupported, and content-mismatched files with clear errors', async () => {
    const service = new MediaService();
    await expect(service.uploadMedia('tree-1', uploadFile(Buffer.from('not a pdf'), 'application/pdf', 'x.pdf')))
      .rejects.toMatchObject({ code: 'INVALID_FILE_TYPE' });
    await expect(service.uploadMedia('tree-1', uploadFile(Buffer.alloc(10 * 1024 * 1024 + 1), 'image/png')))
      .rejects.toMatchObject({ code: 'FILE_TOO_LARGE' });
    await expect(service.uploadMedia('tree-1', uploadFile(onePixelPng, 'application/pdf', 'wrong.pdf')))
      .rejects.toMatchObject({ code: 'INVALID_FILE_TYPE' });
  });

  it('detaches an album without deleting its media', async () => {
    const service = new MediaService();
    const album = await service.createAlbum('tree-1', { title: 'Album' });
    const item = await service.uploadMedia('tree-1', uploadFile(), { albumId: album.id });
    await service.deleteAlbum('tree-1', album.id);
    expect((await getMediaMetadata('tree-1'))[0]).not.toHaveProperty('albumId');
    expect(mockBlobStorage.list('media/tree-1/')).toHaveLength(2);
    expect(await getAlbums('tree-1')).toEqual([]);
    expect(item.id).toBeTruthy();
  });
});
