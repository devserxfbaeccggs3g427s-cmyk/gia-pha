import 'server-only';

import { requireBridgeCookie } from '@/lib/api/spring-guard';
import { springFetch } from '@/lib/api/spring-client';

export interface TreeStatsReport {
    totalMembers: number;
    livingMembers: number;
    deceasedMembers: number;
    generations: number;
    totalRelationships: number;
    totalEvents: number;
    oldestLiving?: { memberId: string; birthYear: number };
    timeline?: Array<{ year: number; eventCount: number }>;
}

export class ReportService {
    async getStatistics(treeId: string): Promise<TreeStatsReport> {
        const cookie = await requireBridgeCookie();
        const res = await springFetch<TreeStatsReport>(
            `/api/reports/${encodeURIComponent(treeId)}/statistics`,
            { method: 'GET' },
            cookie
        );
        return res.body;
    }

    async requestReport(
        treeId: string,
        format: 'json' | 'pdf' | 'svg' | 'png' | 'gedcom',
        options: { includePhotos?: boolean; maxBytes?: number } = {}
    ): Promise<{ jobId: string; status: 'PENDING' | 'READY' | 'FAILED' }> {
        const cookie = await requireBridgeCookie();
        const res = await springFetch<{ jobId: string; status: 'PENDING' | 'READY' | 'FAILED' }>(
            `/api/reports/${encodeURIComponent(treeId)}/generate`,
            { method: 'POST', body: { format, ...options } },
            cookie
        );
        return res.body;
    }

    async getReportStatus(jobId: string): Promise<{ status: string; downloadUrl?: string }> {
        const cookie = await requireBridgeCookie();
        const res = await springFetch<{ status: string; downloadUrl?: string }>(
            `/api/reports/jobs/${encodeURIComponent(jobId)}`,
            { method: 'GET' },
            cookie
        );
        return res.body;
    }
}

export const reportService = new ReportService();
export default reportService;
