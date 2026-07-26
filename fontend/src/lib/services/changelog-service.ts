import 'server-only';

import { nanoid } from 'nanoid';
import type { ChangeAction, ChangeEntityType, ChangeLog } from '@/data/types';
import { ServiceError } from '@/lib/api/service-error';
import { requireBridgeCookie } from '@/lib/api/spring-guard';
import { springFetch } from '@/lib/api/spring-client';

export interface RecordChangeInput {
    treeId: string;
    userId?: string;
    action: ChangeAction;
    entityType?: ChangeEntityType;
    memberId?: string;
    previousData?: Record<string, unknown>;
    newData?: Record<string, unknown>;
    fieldChanged?: string;
    createdAt?: string;
}

interface SpringChangeLogDto {
    id: string;
    treeId: string;
    userId?: string;
    action: ChangeAction;
    entityType?: ChangeEntityType;
    memberId?: string;
    previousData?: Record<string, unknown>;
    newData?: Record<string, unknown>;
    fieldChanged?: string;
    createdAt: string;
}

function fromSpring(dto: SpringChangeLogDto): ChangeLog {
    return {
        id: dto.id,
        treeId: dto.treeId,
        userId: dto.userId ?? 'system',
        action: dto.action,
        entityType: dto.entityType ?? 'MEMBER',
        memberId: dto.memberId,
        previousData: dto.previousData,
        newData: dto.newData,
        fieldChanged: dto.fieldChanged,
        createdAt: dto.createdAt,
    };
}

export class ChangeLogService {
    /**
     * Persist a change-log row. In the microservice decomposition the
     * audit-service is the single writer for audit events (Task 12.1).
     * The BFF forwards the row to {@code /api/audit/change-logs}.
     */
    async recordChange(input: RecordChangeInput): Promise<ChangeLog> {
        if (!input.treeId) throw new ChangeLogError('INVALID_INPUT', 'treeId is required');
        if (!input.action) throw new ChangeLogError('INVALID_INPUT', 'action is required');

        const cookie = await requireBridgeCookie();
        const res = await springFetch<SpringChangeLogDto>(
            '/api/audit/change-logs',
            {
                method: 'POST',
                body: {
                    treeId: input.treeId,
                    userId: input.userId,
                    action: input.action,
                    entityType: input.entityType,
                    memberId: input.memberId,
                    previousData: input.previousData,
                    newData: input.newData,
                    fieldChanged: input.fieldChanged,
                    createdAt: input.createdAt ?? new Date().toISOString()
                }
            },
            cookie
        );
        return fromSpring(res.body);
    }

    async listForTree(treeId: string, options: { limit?: number } = {}): Promise<ChangeLog[]> {
        const cookie = await requireBridgeCookie();
        const res = await springFetch<SpringChangeLogDto[] | { data?: SpringChangeLogDto[] }>(
            `/api/audit/change-logs`,
            {
                method: 'GET',
                public: true,
                query: { treeId, limit: options.limit ?? 100 }
            },
            cookie
        );
        const list = Array.isArray(res.body) ? res.body : res.body?.data ?? [];
        return list.map(fromSpring);
    }
}

export class ChangeLogError extends ServiceError {
    constructor(code: 'NOT_FOUND' | 'INVALID_INPUT', message: string) {
        super(code, message);
        this.name = 'ChangeLogError';
    }
}

export const changeLogService = new ChangeLogService();
export default changeLogService;
