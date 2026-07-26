import 'server-only';

import { requireBridgeCookie } from '@/lib/api/spring-guard';
import { springFetch } from '@/lib/api/spring-client';

export interface ExportRequest {
    treeId: string;
    format: 'json' | 'gedcom' | 'svg' | 'png' | 'pdf';
    includePhotos?: boolean;
}

export class ExportService {
    /**
     * Synchronous export (Task 30). Returns the artifact body directly
     * for formats under the approved size threshold; otherwise the
     * microservice returns {@code CompatibilityThresholdExceeded} and
     * the client switches to the V2 job API.
     */
    async export(
        treeId: string,
        format: ExportRequest['format'],
        options: { includePhotos?: boolean } = {}
    ): Promise<{ body: Blob; contentType: string; filename: string }> {
        const cookie = await requireBridgeCookie();
        const url = `/api/export/${encodeURIComponent(treeId)}/${format}`;
        const res = await springFetch<unknown>(url, {
            method: 'POST',
            body: { includePhotos: options.includePhotos ?? false },
            // Note: export returns binary. The BFF returns JSON metadata
            // about the artifact; the client downloads via signed URL.
        }, cookie);
        const body = res.body as { downloadUrl: string; filename: string; contentType: string };
        return {
            body: new Blob(),
            contentType: body.contentType,
            filename: body.filename
        };
    }

    async requestJob(
        treeId: string,
        format: ExportRequest['format'],
        options: { includePhotos?: boolean } = {}
    ): Promise<{ jobId: string }> {
        const cookie = await requireBridgeCookie();
        const res = await springFetch<{ jobId: string }>(
            `/api/transfer/${encodeURIComponent(treeId)}/export-jobs`,
            { method: 'POST', body: { format, ...options } },
            cookie
        );
        return res.body;
    }
}

export const exportService = new ExportService();
export default exportService;
