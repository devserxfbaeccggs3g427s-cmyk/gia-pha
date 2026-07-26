import 'server-only';

import { requireBridgeCookie } from '@/lib/api/spring-guard';
import { springFetch } from '@/lib/api/spring-client';

export interface ImportPreview {
    jobId: string;
    memberCount: number;
    relationshipCount: number;
    eventCount: number;
    warnings: string[];
    samples: unknown[];
}

export interface ImportExecuteInput {
    jobId: string;
    treeId: string;
    mode: 'append' | 'replace';
    remapStrategy: 'skip' | 'overwrite' | 'regenerate';
}

export class ImportService {
    async preview(
        treeId: string,
        input: {
            format: 'gedcom' | 'json' | 'csv';
            filename: string;
            mimeType: string;
            size: number;
            bytes: ArrayBuffer | string;
        }
    ): Promise<ImportPreview> {
        const cookie = await requireBridgeCookie();
        const res = await springFetch<ImportPreview>(
            `/api/trees/${encodeURIComponent(treeId)}/imports/preview`,
            {
                method: 'POST',
                body: {
                    format: input.format,
                    filename: input.filename,
                    mimeType: input.mimeType,
                    size: input.size
                }
            },
            cookie
        );
        return res.body;
    }

    async execute(input: ImportExecuteInput): Promise<{ jobId: string; status: 'RUNNING' | 'DONE' | 'FAILED' }> {
        const cookie = await requireBridgeCookie();
        const res = await springFetch<{ jobId: string; status: 'RUNNING' | 'DONE' | 'FAILED' }>(
            `/api/trees/${encodeURIComponent(input.treeId)}/imports/execute`,
            { method: 'POST', body: input },
            cookie
        );
        return res.body;
    }
}

export const importService = new ImportService();
export default importService;
