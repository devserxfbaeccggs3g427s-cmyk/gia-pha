import { nanoid } from 'nanoid';
import {
  createAlbumSchema,
  mediaUploadSchema,
  updateAlbumSchema,
  type CreateAlbumInput,
  type UpdateAlbumInput
} from '@/data/schemas';
import type { Album, Event, MediaMetadata } from '@/data/types';
import { BLOB_PATHS, deleteBlobs, writeBinaryBlob } from '@/lib/blob/client';
import { getAlbums, getEvents, getMediaMetadata, getMembers } from '@/lib/blob/readers';
import { putAlbums, putEvents, putMediaMetadata, putMembers } from '@/lib/blob/writers';
import { changeLogService } from './changelog-service';
import { MediaServiceError } from './event-media-errors';

export { MediaServiceError } from './event-media-errors';

export const MAX_MEDIA_SIZE = 10 * 1024 * 1024;
export const MEDIA_MIME_TYPES = [
  'image/jpeg',
  'image/png',
  'image/webp',
  'application/pdf'
] as const;
export type SupportedMediaMimeType = (typeof MEDIA_MIME_TYPES)[number];
export type MediaMutationActor = string | { userId?: string } | undefined;

export interface UploadableFile {
  name: string;
  type: string;
  size: number;
  arrayBuffer(): Promise<ArrayBuffer>;
}

export interface MediaUploadDetails {
  // filename/originalName/mimeType/fileSize are accepted for callers that
  // already normalized multipart metadata; the server always derives these
  // values from the uploaded bytes and file name.
  filename?: string;
  originalName?: string;
  mimeType?: string;
  fileSize?: number;
  isAvatar?: boolean;
  memberId?: string;
  eventId?: string;
  memberIds?: string[];
  eventIds?: string[];
  albumId?: string;
  caption?: string;
  takenAt?: string;
}

export class MediaService {
  async uploadMedia(
    treeId: string,
    file: File | UploadableFile,
    metadata: MediaUploadDetails = {},
    actor: MediaMutationActor = undefined
  ): Promise<MediaMetadata> {
    assertIdentifier(treeId, 'treeId');
    assertUploadableFile(file);
    const mimeType = normalizeMimeType(file.type);
    if (!MEDIA_MIME_TYPES.includes(mimeType as SupportedMediaMimeType)) {
      throw new MediaServiceError(
        'INVALID_FILE_TYPE',
        'Chỉ hỗ trợ tệp JPEG, PNG, WebP hoặc PDF'
      );
    }
    if (file.size <= 0) throw new MediaServiceError('INVALID_INPUT', 'Tệp tải lên không được rỗng');
    if (file.size > MAX_MEDIA_SIZE) {
      throw new MediaServiceError('FILE_TOO_LARGE', 'Kích thước tệp không được vượt quá 10MB');
    }

    const bytes = Buffer.from(await file.arrayBuffer());
    if (bytes.length <= 0) throw new MediaServiceError('INVALID_INPUT', 'Tệp tải lên không được rỗng');
    if (bytes.length > MAX_MEDIA_SIZE) {
      throw new MediaServiceError('FILE_TOO_LARGE', 'Kích thước tệp không được vượt quá 10MB');
    }
    if (!matchesFileSignature(bytes, mimeType)) {
      throw new MediaServiceError(
        'INVALID_FILE_TYPE',
        'Nội dung tệp không khớp với định dạng JPEG, PNG, WebP hoặc PDF đã khai báo'
      );
    }

    const id = nanoid();
    const extension = extensionForMime(mimeType);
    const filename = `${id}.${extension}`;
    const input = mediaUploadSchema.parse({
      ...metadata,
      filename,
      originalName: normalizeOriginalName(file.name),
      mimeType,
      fileSize: bytes.length
    });
    const [currentMedia, members, events, albums] = await Promise.all([
      getMediaMetadata(treeId),
      getMembers(treeId),
      getEvents(treeId),
      getAlbums(treeId)
    ]);
    validateLinks(input.memberIds, input.eventIds, input.albumId, members, events, albums);
    if (input.isAvatar && !input.memberId) {
      throw new MediaServiceError('INVALID_INPUT', 'Ảnh đại diện phải được liên kết với một thành viên');
    }
    if (input.isAvatar && !mimeType.startsWith('image/')) {
      throw new MediaServiceError('INVALID_FILE_TYPE', 'Ảnh đại diện phải là tệp JPEG, PNG hoặc WebP');
    }

    const originalPath = BLOB_PATHS.mediaOriginal(treeId, filename);
    const uploadedUrls: string[] = [];
    let originalUrl: string;
    let thumbnailUrl: string | undefined;
    try {
      const original = await writeBinaryBlob(originalPath, bytes, mimeType);
      originalUrl = original.url;
      uploadedUrls.push(original.url);

      if (mimeType.startsWith('image/')) {
        let thumbnail: Buffer;
        try {
          const { default: sharp } = await import('sharp');
          thumbnail = await sharp(bytes, { failOn: 'error', limitInputPixels: 40_000_000 })
            .rotate()
            .resize({ width: 480, height: 480, fit: 'inside', withoutEnlargement: true })
            .webp({ quality: 78, effort: 4 })
            .toBuffer();
        } catch (error) {
          throw new MediaServiceError('INVALID_FILE_TYPE', 'Không thể xử lý tệp ảnh bị hỏng');
        }
        const storedThumbnail = await writeBinaryBlob(
          BLOB_PATHS.mediaThumbnail(treeId, `${id}.webp`),
          thumbnail,
          'image/webp'
        );
        thumbnailUrl = storedThumbnail.url;
        uploadedUrls.push(storedThumbnail.url);
      }
    } catch (error) {
      await bestEffort(() => deleteBlobs(uploadedUrls));
      throw error;
    }

    const uploadedAt = new Date().toISOString();
    const item: MediaMetadata = {
      id,
      treeId,
      memberIds: input.memberIds,
      eventIds: input.eventIds,
      ...(input.memberIds[0] ? { memberId: input.memberIds[0] } : {}),
      ...(input.eventIds[0] ? { eventId: input.eventIds[0] } : {}),
      ...(input.albumId ? { albumId: input.albumId } : {}),
      filename,
      originalName: input.originalName,
      mimeType,
      fileSize: bytes.length,
      blobUrl: originalUrl!,
      ...(thumbnailUrl ? { thumbnailUrl } : {}),
      contentUrl: mediaContentUrl(treeId, id, false),
      ...(thumbnailUrl ? { thumbnailContentUrl: mediaContentUrl(treeId, id, true) } : {}),
      ...(input.caption !== undefined ? { caption: input.caption } : {}),
      ...(input.takenAt !== undefined ? { takenAt: input.takenAt } : {}),
      uploadedAt
    };
    const nextMedia = [...currentMedia, item];
    const nextEvents = addMediaToEvents(events, item.id, input.eventIds);
    const nextMembers = input.isAvatar
      ? members.map((member) => member.id === input.memberId
        ? { ...member, avatarMediaId: item.id, updatedAt: uploadedAt }
        : member
      )
      : members;

    try {
      await putMediaMetadata(treeId, nextMedia);
      if (!sameJson(events, nextEvents)) await putEvents(treeId, nextEvents);
      if (!sameJson(members, nextMembers)) await putMembers(treeId, nextMembers);
    } catch (error) {
      await bestEffort(() => putMediaMetadata(treeId, currentMedia));
      await bestEffort(() => putEvents(treeId, events));
      await bestEffort(() => putMembers(treeId, members));
      await bestEffort(() => deleteBlobs(uploadedUrls));
      throw error;
    }
    await changeLogService.recordChange({
      treeId,
      userId: actorId(actor),
      action: 'CREATE',
      entityType: 'MEDIA',
      newData: toRecord(item),
      createdAt: uploadedAt
    });
    if (input.isAvatar) {
      const previousMember = members.find((member) => member.id === input.memberId);
      const nextMember = nextMembers.find((member) => member.id === input.memberId);
      if (previousMember && nextMember) {
        await changeLogService.recordChange({
          treeId,
          userId: actorId(actor),
          memberId: previousMember.id,
          action: 'UPDATE',
          entityType: 'MEMBER',
          previousData: previousMember as unknown as Record<string, unknown>,
          newData: nextMember as unknown as Record<string, unknown>,
          fieldChanged: 'avatarMediaId',
          createdAt: uploadedAt
        });
      }
    }
    return item;
  }

  async deleteMedia(
    treeId: string,
    mediaId: string,
    actor: MediaMutationActor = undefined
  ): Promise<MediaMetadata> {
    assertIdentifier(treeId, 'treeId');
    assertIdentifier(mediaId, 'mediaId');
    const [media, events, members] = await Promise.all([
      getMediaMetadata(treeId),
      getEvents(treeId),
      getMembers(treeId)
    ]);
    const item = media.find((candidate) => candidate.id === mediaId);
    if (!item) throw new MediaServiceError('NOT_FOUND', 'Media not found');

    const nextMedia = media.filter((candidate) => candidate.id !== mediaId);
    const nextEvents = events.map((event) => eventMediaIds(event).includes(mediaId)
      ? { ...event, mediaIds: eventMediaIds(event).filter((id) => id !== mediaId), updatedAt: new Date().toISOString() }
      : event
    );
    const nextMembers = members.map((member) => member.avatarMediaId === mediaId
      ? { ...member, avatarMediaId: undefined, updatedAt: new Date().toISOString() }
      : member
    );
    await putMediaMetadata(treeId, nextMedia);
    try {
      if (!sameJson(events, nextEvents)) await putEvents(treeId, nextEvents);
      if (!sameJson(members, nextMembers)) await putMembers(treeId, nextMembers);
    } catch (error) {
      await bestEffort(() => putMediaMetadata(treeId, media));
      await bestEffort(() => putEvents(treeId, events));
      await bestEffort(() => putMembers(treeId, members));
      throw error;
    }

    // Metadata is removed first so a deletion failure can only leave an
    // unreachable orphan blob, never a gallery entry pointing to a 404.
    try {
      await deleteBlobs([item.blobUrl, ...(item.thumbnailUrl ? [item.thumbnailUrl] : [])]);
    } catch (error) {
      // The user-visible record is already gone. Keep the mutation successful
      // and surface the orphan cleanup issue to server logs for maintenance.
      console.error(`[media] failed to clean up blobs for ${mediaId}`, error);
    }
    await changeLogService.recordChange({
      treeId,
      userId: actorId(actor),
      action: 'DELETE',
      entityType: 'MEDIA',
      previousData: toRecord(item)
    });
    return item;
  }

  async getMediaForTree(treeId: string): Promise<MediaMetadata[]> {
    assertIdentifier(treeId, 'treeId');
    return sortMedia(await getMediaMetadata(treeId));
  }

  async getMedia(treeId: string, mediaId: string): Promise<MediaMetadata> {
    assertIdentifier(treeId, 'treeId');
    assertIdentifier(mediaId, 'mediaId');
    const item = (await getMediaMetadata(treeId)).find((candidate) => candidate.id === mediaId);
    if (!item) throw new MediaServiceError('NOT_FOUND', 'Media not found');
    return item;
  }

  async getMediaForMember(treeId: string, memberId: string): Promise<MediaMetadata[]> {
    assertIdentifier(treeId, 'treeId');
    assertIdentifier(memberId, 'memberId');
    return sortMedia((await getMediaMetadata(treeId)).filter((item) => mediaMemberIds(item).includes(memberId)));
  }

  async getMediaForEvent(treeId: string, eventId: string): Promise<MediaMetadata[]> {
    assertIdentifier(treeId, 'treeId');
    assertIdentifier(eventId, 'eventId');
    const [media, events] = await Promise.all([getMediaMetadata(treeId), getEvents(treeId)]);
    const event = events.find((candidate) => candidate.id === eventId);
    const linkedIds = new Set(event?.mediaIds ?? []);
    return sortMedia(media.filter((item) => linkedIds.has(item.id) || mediaEventIds(item).includes(eventId)));
  }

  async getMediaForAlbum(treeId: string, albumId: string): Promise<MediaMetadata[]> {
    assertIdentifier(treeId, 'treeId');
    assertIdentifier(albumId, 'albumId');
    return sortMedia((await getMediaMetadata(treeId)).filter((item) => item.albumId === albumId));
  }

  generateThumbnailUrl(blobUrl: string, width = 480): string {
    if (!/^https?:\/\//.test(blobUrl)) {
      throw new MediaServiceError('INVALID_INPUT', 'blobUrl must be an absolute URL');
    }
    if (!Number.isInteger(width) || width < 16 || width > 3840) {
      throw new MediaServiceError('INVALID_INPUT', 'width must be an integer between 16 and 3840');
    }
    return `/_next/image?url=${encodeURIComponent(blobUrl)}&w=${width}&q=75`;
  }

  async createAlbum(
    treeId: string,
    data: unknown,
    actor: MediaMutationActor = undefined
  ): Promise<Album> {
    assertIdentifier(treeId, 'treeId');
    const input: CreateAlbumInput = createAlbumSchema.parse(data);
    const albums = await getAlbums(treeId);
    const now = new Date().toISOString();
    const album: Album = { id: nanoid(), treeId, ...input, createdAt: now, updatedAt: now };
    await putAlbums(treeId, [...albums, album]);
    await changeLogService.recordChange({
      treeId,
      userId: actorId(actor),
      action: 'CREATE',
      entityType: 'MEDIA',
      newData: { kind: 'ALBUM', ...album },
      createdAt: now
    });
    return album;
  }

  async updateAlbum(
    treeId: string,
    albumId: string,
    data: unknown,
    actor: MediaMutationActor = undefined
  ): Promise<Album> {
    assertIdentifier(treeId, 'treeId');
    assertIdentifier(albumId, 'albumId');
    const input: UpdateAlbumInput = updateAlbumSchema.parse(data);
    const albums = await getAlbums(treeId);
    const index = albums.findIndex((album) => album.id === albumId);
    if (index < 0) throw new MediaServiceError('NOT_FOUND', 'Album not found');
    const previous = albums[index];
    const next = { ...previous, ...input, updatedAt: new Date().toISOString() };
    albums[index] = next;
    await putAlbums(treeId, albums);
    await changeLogService.recordChange({
      treeId,
      userId: actorId(actor),
      action: 'UPDATE',
      entityType: 'MEDIA',
      previousData: { kind: 'ALBUM', ...previous },
      newData: { kind: 'ALBUM', ...next },
      fieldChanged: Object.keys(input).join(','),
      createdAt: next.updatedAt
    });
    return next;
  }

  async deleteAlbum(
    treeId: string,
    albumId: string,
    actor: MediaMutationActor = undefined
  ): Promise<Album> {
    assertIdentifier(treeId, 'treeId');
    assertIdentifier(albumId, 'albumId');
    const [albums, media] = await Promise.all([getAlbums(treeId), getMediaMetadata(treeId)]);
    const album = albums.find((candidate) => candidate.id === albumId);
    if (!album) throw new MediaServiceError('NOT_FOUND', 'Album not found');
    const nextAlbums = albums.filter((candidate) => candidate.id !== albumId);
    const nextMedia = media.map((item) => item.albumId === albumId
      ? { ...item, albumId: undefined }
      : item
    );
    await putAlbums(treeId, nextAlbums);
    try {
      if (!sameJson(media, nextMedia)) await putMediaMetadata(treeId, nextMedia);
    } catch (error) {
      await bestEffort(() => putAlbums(treeId, albums));
      throw error;
    }
    await changeLogService.recordChange({
      treeId,
      userId: actorId(actor),
      action: 'DELETE',
      entityType: 'MEDIA',
      previousData: { kind: 'ALBUM', ...album }
    });
    return album;
  }

  async getAlbums(treeId: string): Promise<Album[]> {
    assertIdentifier(treeId, 'treeId');
    return (await getAlbums(treeId)).sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  }
}

function validateLinks(
  memberIds: string[],
  eventIds: string[],
  albumId: string | undefined,
  members: Array<{ id: string }>,
  events: Array<{ id: string }>,
  albums: Array<{ id: string }>
): void {
  const knownMembers = new Set(members.map((item) => item.id));
  const knownEvents = new Set(events.map((item) => item.id));
  const missingMembers = memberIds.filter((id) => !knownMembers.has(id));
  const missingEvents = eventIds.filter((id) => !knownEvents.has(id));
  const messages = [
    ...(missingMembers.length ? [`Members not found: ${missingMembers.join(', ')}`] : []),
    ...(missingEvents.length ? [`Events not found: ${missingEvents.join(', ')}`] : []),
    ...(albumId && !albums.some((album) => album.id === albumId) ? [`Album not found: ${albumId}`] : [])
  ];
  if (messages.length) throw new MediaServiceError('INVALID_INPUT', messages.join('; '));
}

function addMediaToEvents(events: Event[], mediaId: string, eventIds: string[]): Event[] {
  const linked = new Set(eventIds);
  if (!linked.size) return events;
  const now = new Date().toISOString();
  return events.map((event) => linked.has(event.id) && !eventMediaIds(event).includes(mediaId)
    ? { ...event, mediaIds: [...eventMediaIds(event), mediaId], updatedAt: now }
    : event
  );
}

function mediaMemberIds(item: MediaMetadata): string[] {
  return [...new Set([...(item.memberIds ?? []), ...(item.memberId ? [item.memberId] : [])])];
}

function mediaEventIds(item: MediaMetadata): string[] {
  return [...new Set([...(item.eventIds ?? []), ...(item.eventId ? [item.eventId] : [])])];
}

function eventMediaIds(event: Event): string[] {
  return [...new Set(event.mediaIds ?? [])];
}

function sortMedia(items: MediaMetadata[]): MediaMetadata[] {
  return [...items].sort((a, b) => (b.takenAt ?? b.uploadedAt).localeCompare(a.takenAt ?? a.uploadedAt));
}

function matchesFileSignature(bytes: Buffer, mimeType: string): boolean {
  if (mimeType === 'image/jpeg') return bytes.length >= 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff;
  if (mimeType === 'image/png') {
    return bytes.length >= 8 && bytes.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]));
  }
  if (mimeType === 'image/webp') {
    return bytes.length >= 12 && bytes.toString('ascii', 0, 4) === 'RIFF' && bytes.toString('ascii', 8, 12) === 'WEBP';
  }
  if (mimeType === 'application/pdf') return bytes.length >= 5 && bytes.toString('ascii', 0, 5) === '%PDF-';
  return false;
}

function extensionForMime(mimeType: string): string {
  switch (mimeType) {
    case 'image/jpeg': return 'jpg';
    case 'image/png': return 'png';
    case 'image/webp': return 'webp';
    case 'application/pdf': return 'pdf';
    default: throw new MediaServiceError('INVALID_FILE_TYPE', 'Unsupported media type');
  }
}

function normalizeMimeType(value: string): string {
  return value.toLowerCase().split(';', 1)[0].trim();
}

function normalizeOriginalName(value: string): string {
  const leaf = value.replace(/\\/g, '/').split('/').at(-1)?.trim() ?? '';
  if (!leaf) throw new MediaServiceError('INVALID_INPUT', 'Tên tệp không hợp lệ');
  return leaf.slice(0, 255);
}

function assertUploadableFile(value: unknown): asserts value is UploadableFile {
  if (
    typeof value !== 'object' || value === null ||
    typeof (value as UploadableFile).name !== 'string' ||
    typeof (value as UploadableFile).type !== 'string' ||
    typeof (value as UploadableFile).size !== 'number' ||
    typeof (value as UploadableFile).arrayBuffer !== 'function'
  ) {
    throw new MediaServiceError('INVALID_INPUT', 'Trường file là bắt buộc');
  }
}

function mediaContentUrl(treeId: string, mediaId: string, thumbnail: boolean): string {
  return `/api/media/${encodeURIComponent(mediaId)}/content?treeId=${encodeURIComponent(treeId)}${thumbnail ? '&thumbnail=true' : ''}`;
}

function assertIdentifier(value: string, name: string): void {
  if (!value?.trim()) throw new MediaServiceError('INVALID_INPUT', `${name} is required`);
}

function actorId(actor: MediaMutationActor): string {
  return typeof actor === 'string' ? actor : actor?.userId ?? 'system';
}

function toRecord(value: MediaMetadata): Record<string, unknown> {
  return value as unknown as Record<string, unknown>;
}

function sameJson(left: unknown, right: unknown): boolean {
  return JSON.stringify(left) === JSON.stringify(right);
}

async function bestEffort(operation: () => Promise<void>): Promise<void> {
  try {
    await operation();
  } catch {
    // Preserve the primary operation error after a compensating action fails.
  }
}

export const mediaService = new MediaService();
export default mediaService;

export const uploadMedia = mediaService.uploadMedia.bind(mediaService);
export const deleteMedia = mediaService.deleteMedia.bind(mediaService);
export const getMediaForMember = mediaService.getMediaForMember.bind(mediaService);
export const getMediaForEvent = mediaService.getMediaForEvent.bind(mediaService);
