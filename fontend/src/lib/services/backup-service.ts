import 'server-only';

import { requireBridgeCookie } from '@/lib/api/spring-guard';
import { springFetch } from '@/lib/api/spring-client';

export interface BackupSnapshot {
    snapshotId: string;
    treeId: string;
    createdAt: string;
    size: number;
    sha256: string;
}

export class BackupService {
    async list(treeId: string): Promise<BackupSnapshot[]> {
        const cookie = await requireBridgeCookie();
        const res = await springFetch<{ snapshots: BackupSnapshot[] } | BackupSnapshot[]>(
            `/api/backup/${encodeURIComponent(treeId)}`,
            { method: 'GET' },
            cookie
        );
        if (Array.isArray(res.body)) return res.body;
        return res.body?.snapshots ?? [];
    }

    async create(treeId: string): Promise<BackupSnapshot> {
        const cookie = await requireBridgeCookie();
        const res = await springFetch<BackupSnapshot>(
            `/api/backup/${encodeURIComponent(treeId)}`,
            { method: 'POST' },
            cookie
        );
        return res.body;
    }

    async restore(treeId: string, snapshotId: string): Promise<{ jobId: string }> {
        const cookie = await requireBridgeCookie();
        const res = await springFetch<{ jobId: string }>(
            `/api/transfer/${encodeURIComponent(treeId)}/restore`,
            { method: 'POST', body: { snapshotId } },
            cookie
        );
        return res.body;
    }
}

export const backupService = new BackupService();
export default backupService;
