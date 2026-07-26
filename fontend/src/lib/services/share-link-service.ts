import 'server-only';

import { nanoid } from 'nanoid';
import type { ShareLink, SharePermission } from '@/data/types';
import { ServiceError } from '@/lib/api/service-error';
import { requireBridgeCookie } from '@/lib/api/spring-guard';
import { springFetch } from '@/lib/api/spring-client';
import {
    fromSpringShareLink,
    toSpringShareLink,
    type SpringShareLinkDto
} from '@/lib/api/dto-mapper';

export class ShareLinkServiceError extends ServiceError {
    constructor(code: 'NOT_FOUND' | 'INVALID_INPUT' | 'CONFLICT', message: string) {
        super(code, message);
        this.name = 'ShareLinkServiceError';
    }
}

async function fetchShareLinks(treeId: string, cookie: string): Promise<ShareLink[]> {
    const res = await springFetch<SpringShareLinkDto[] | { data?: SpringShareLinkDto[] }>(
        `/api/trees/${encodeURIComponent(treeId)}/shares`,
        { method: 'GET', public: true },
        cookie
    );
    const list = Array.isArray(res.body) ? res.body : res.body?.data ?? [];
    return list.map(fromSpringShareLink);
}

export class ShareLinkService {
    async listShareLinks(treeId: string): Promise<ShareLink[]> {
        const cookie = await requireBridgeCookie();
        return fetchShareLinks(treeId, cookie);
    }

    async createShareLink(
        treeId: string,
        input: {
            permission?: SharePermission;
            expiresAt?: string;
            createdBy: string;
        }
    ): Promise<ShareLink> {
        const cookie = await requireBridgeCookie();
        const now = new Date().toISOString();
        const link: ShareLink = {
            id: nanoid(),
            treeId,
            token: nanoid(32),
            permission: input.permission ?? 'VIEW',
            expiresAt: input.expiresAt ?? new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
            createdAt: now,
            createdBy: input.createdBy
        };
        const res = await springFetch<SpringShareLinkDto>(
            `/api/trees/${encodeURIComponent(treeId)}/shares`,
            { method: 'POST', body: toSpringShareLink(link) },
            cookie
        );
        return fromSpringShareLink(res.body);
    }

    async revokeShareLink(treeId: string, linkId: string): Promise<void> {
        const cookie = await requireBridgeCookie();
        await springFetch<unknown>(
            `/api/trees/${encodeURIComponent(treeId)}/shares/${encodeURIComponent(linkId)}`,
            { method: 'DELETE' },
            cookie
        );
    }

    async resolvePublicLink(token: string): Promise<{
        treeId: string;
        permission: SharePermission;
        publicView: unknown;
    }> {
        const res = await springFetch<{
            treeId: string;
            permission: SharePermission;
            publicView: unknown;
        }>(`/api/public/share/${encodeURIComponent(token)}`, {
            method: 'GET',
            public: true,
            headers: { 'Cache-Control': 'no-store' }
        });
        return res.body;
    }

    async getSharedTree(token: string): Promise<SharedTreeView> {
        const cookie = await requireBridgeCookie();
        const res = await springFetch<Partial<SharedTreeView> & { publicView?: unknown }>(
            `/api/public/share/${encodeURIComponent(token)}`,
            { method: 'GET', public: true, headers: { 'Cache-Control': 'no-store' } },
            cookie
        );
        const view = res.body ?? {};
        const inner = (view as { publicView?: Partial<SharedTreeView> }).publicView ?? {};
        const merged: SharedTreeView = {
            tree: inner.tree ?? view.tree ?? { name: 'Gia phả', description: '' },
            shareLink: inner.shareLink ?? view.shareLink ?? { expiresAt: new Date(Date.now() + 7 * 24 * 3600 * 1000).toISOString() },
            members: inner.members ?? view.members ?? [],
            relationships: inner.relationships ?? view.relationships ?? [],
            events: inner.events ?? view.events ?? []
        };
        return merged;
    }
}

export interface SharedTreeView {
    tree: { name: string; description?: string };
    shareLink: { expiresAt: string };
    members: Array<{
        id: string;
        generation?: number;
        gender?: string;
        fullName: string;
        isAlive?: boolean;
        occupation?: string;
        placeOfBirth?: string;
        dateOfBirth?: string | null;
        dateOfDeath?: string | null;
    }>;
    relationships: Array<{
        id: string;
        sourceMemberId: string;
        targetMemberId: string;
        type: string;
    }>;
    events: Array<{
        id: string;
        title: string;
        eventDate: string;
        location?: string;
        description?: string;
    }>;
}

export const shareLinkService = new ShareLinkService();
export default shareLinkService;
