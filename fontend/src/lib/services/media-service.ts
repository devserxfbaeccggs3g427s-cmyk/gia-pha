import 'server-only';

import { nanoid } from 'nanoid';
import { createAlbumSchema } from '@/data/schemas';
import type { Album, MediaMetadata } from '@/data/types';
import { ServiceError } from '@/lib/api/service-error';
import { requireBridgeCookie } from '@/lib/api/spring-guard';
import { springFetch } from '@/lib/api/spring-client';
import {
    fromSpringAlbum,
    fromSpringMedia,
    toSpringAlbum,
    type SpringAlbumDto,
    type SpringMediaDto
} from '@/lib/api/dto-mapper';

export class MediaServiceError extends ServiceError {
    constructor(
        code:
            | 'NOT_FOUND'
            | 'INVALID_INPUT'
            | 'INVALID_FILE_TYPE'
            | 'FILE_TOO_LARGE'
            | 'CONFLICT',
        message: string
    ) {
        super(code as 'NOT_FOUND' | 'INVALID_INPUT' | 'CONFLICT', message);
        this.name = 'MediaServiceError';
    }
}

async function fetchAlbums(treeId: string, cookie: string): Promise<Album[]> {
    const res = await springFetch<SpringAlbumDto[] | { data?: SpringAlbumDto[] }>(
        `/api/trees/${encodeURIComponent(treeId)}/albums`,
        { method: 'GET', public: true },
        cookie
    );
    const list = Array.isArray(res.body) ? res.body : res.body?.data ?? [];
    return list.map(fromSpringAlbum);
}

async function fetchMedia(treeId: string, cookie: string): Promise<MediaMetadata[]> {
    const res = await springFetch<SpringMediaDto[] | { data?: SpringMediaDto[] }>(
        `/api/trees/${encodeURIComponent(treeId)}/media`,
        { method: 'GET', public: true },
        cookie
    );
    const list = Array.isArray(res.body) ? res.body : res.body?.data ?? [];
    return list.map(fromSpringMedia);
}

export class MediaService {
    async getAlbums(treeId: string): Promise<Album[]> {
        const cookie = await requireBridgeCookie();
        return fetchAlbums(treeId, cookie);
    }

    async getAlbum(treeId: string, albumId: string): Promise<Album> {
        const cookie = await requireBridgeCookie();
        const res = await springFetch<SpringAlbumDto>(
            `/api/trees/${encodeURIComponent(treeId)}/albums/${encodeURIComponent(albumId)}`,
            { method: 'GET' },
            cookie
        );
        return fromSpringAlbum(res.body);
    }

    async createAlbum(treeId: string, data: unknown, actor?: string): Promise<Album> {
        const input = createAlbumSchema.parse(data);
        const cookie = await requireBridgeCookie();
        const album: Album = {
            ...input,
            id: nanoid(),
            treeId,
            mediaIds: input.mediaIds ?? [],
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString()
        };
        const res = await springFetch<SpringAlbumDto>(
            `/api/trees/${encodeURIComponent(treeId)}/albums`,
            { method: 'POST', body: toSpringAlbum(album) },
            cookie
        );
        return fromSpringAlbum(res.body);
    }

    async listMedia(
        treeId: string,
        filters: { memberId?: string; eventId?: string; albumId?: string } = {}
    ): Promise<MediaMetadata[]> {
        const cookie = await requireBridgeCookie();
        const all = await fetchMedia(treeId, cookie);
        return all.filter((item) => {
            if (filters.memberId) {
                const ids = item.memberIds ?? (item.memberId ? [item.memberId] : []);
                if (!ids.includes(filters.memberId)) return false;
            }
            if (filters.eventId) {
                const ids = item.eventIds ?? (item.eventId ? [item.eventId] : []);
                if (!ids.includes(filters.eventId)) return false;
            }
            if (filters.albumId && item.albumId !== filters.albumId) return false;
            return true;
        });
    }

    async getMedia(treeId: string, mediaId: string): Promise<MediaMetadata> {
        const cookie = await requireBridgeCookie();
        const res = await springFetch<SpringMediaDto>(
            `/api/trees/${encodeURIComponent(treeId)}/media/${encodeURIComponent(mediaId)}`,
            { method: 'GET' },
            cookie
        );
        return fromSpringMedia(res.body);
    }

    async deleteMedia(treeId: string, mediaId: string): Promise<void> {
        const cookie = await requireBridgeCookie();
        await springFetch<unknown>(
            `/api/trees/${encodeURIComponent(treeId)}/media/${encodeURIComponent(mediaId)}`,
            { method: 'DELETE' },
            cookie
        );
    }

    async updateAlbum(treeId: string, albumId: string, data: unknown, actor?: string): Promise<Album> {
        const cookie = await requireBridgeCookie();
        const res = await springFetch<SpringAlbumDto>(
            `/api/trees/${encodeURIComponent(treeId)}/albums/${encodeURIComponent(albumId)}`,
            { method: 'PATCH', body: data },
            cookie
        );
        return fromSpringAlbum(res.body);
    }

    async deleteAlbum(treeId: string, albumId: string, actor?: string): Promise<void> {
        const cookie = await requireBridgeCookie();
        await springFetch<unknown>(
            `/api/trees/${encodeURIComponent(treeId)}/albums/${encodeURIComponent(albumId)}`,
            { method: 'DELETE' },
            cookie
        );
    }

    /**
     * Forward an upload to the binary-storage service. The microservice
     * returns an upload intent (signed URL) that the BFF returns to the
     * client; the client then uploads the bytes directly to the signed
     * Blob URL, then calls {@code /complete-upload} to activate the
     * media record.
     */
    async requestUpload(
        treeId: string,
        input: {
            filename: string;
            mimeType: string;
            size: number;
            memberId?: string;
            eventId?: string;
            albumId?: string;
        }
    ): Promise<{ uploadId: string; uploadUrl: string; expiresAt: string }> {
        const cookie = await requireBridgeCookie();
        const res = await springFetch<{
            uploadId: string;
            uploadUrl: string;
            expiresAt: string;
        }>(
            `/api/trees/${encodeURIComponent(treeId)}/media/upload`,
            { method: 'POST', body: input },
            cookie
        );
        return res.body;
    }

    /**
     * Activate an uploaded media record. Called after the client uploads
     * bytes directly to the signed Blob URL.
     */
    async completeUpload(
        treeId: string,
        input: {
            uploadId: string;
            memberIds?: string[];
            eventIds?: string[];
            albumId?: string;
            isAvatar?: boolean;
            caption?: string;
            takenAt?: string;
        }
    ): Promise<MediaMetadata> {
        const cookie = await requireBridgeCookie();
        const res = await springFetch<SpringMediaDto>(
            `/api/trees/${encodeURIComponent(treeId)}/media/complete`,
            { method: 'POST', body: input },
            cookie
        );
        return fromSpringMedia(res.body);
    }

    async uploadIntent(
        treeId: string,
        input: {
            filename: string;
            mimeType: string;
            size: number;
            memberId?: string;
            eventId?: string;
            albumId?: string;
        }
    ): Promise<{ uploadId: string; uploadUrl: string; expiresAt: string }> {
        const cookie = await requireBridgeCookie();
        const res = await springFetch<{
            uploadId: string;
            uploadUrl: string;
            expiresAt: string;
        }>(
            `/api/trees/${encodeURIComponent(treeId)}/media/upload`,
            { method: 'POST', body: input },
            cookie
        );
        return res.body;
    }
}

export const mediaService = new MediaService();
export default mediaService;
