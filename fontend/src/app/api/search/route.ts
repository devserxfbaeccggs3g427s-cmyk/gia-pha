import { NextResponse } from 'next/server';
import { z } from 'zod';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { serviceRouteError } from '@/lib/api/service-error-handler';
import { searchService } from '@/lib/services/search-service';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

const querySchema = z.object({
    treeId: z.string().trim().min(1),
    q: z.string().trim().min(1).optional(),
    mode: z.enum(['search', 'autocomplete', 'filter']).default('search'),
    fields: z.string().optional(),
    limit: z.coerce.number().int().min(1).max(100).default(50)
});

export async function GET(request: Request): Promise<NextResponse> {
    try {
        const params = new URL(request.url).searchParams;
        const query = querySchema.parse({
            treeId: params.get('treeId') ?? '',
            q: params.get('q') ?? undefined,
            mode: params.get('mode') ?? 'search',
            fields: params.get('fields') ?? undefined,
            limit: params.get('limit') ?? undefined
        });

        const userId = await requireAuthenticatedUserId();
        await requireTreePermission(query.treeId, userId, 'READ');

        if (query.mode === 'autocomplete') {
            if (!query.q) {
                return NextResponse.json(
                    { ok: false, error: { code: 'INVALID_INPUT', message: 'q là bắt buộc cho autocomplete' } },
                    { status: 400 }
                );
            }
            const results = await searchService.searchMembers(query.treeId, query.q, { limit: query.limit });
            return NextResponse.json(results.slice(0, query.limit));
        }

        if (!query.q) {
            return NextResponse.json(
                { ok: false, error: { code: 'INVALID_INPUT', message: 'q là bắt buộc cho search' } },
                { status: 400 }
            );
        }

        const results = await searchService.searchMembers(query.treeId, query.q, { limit: query.limit });
        return NextResponse.json({
            ok: true,
            treeId: query.treeId,
            query: query.q,
            total: results.length,
            results
        });
    } catch (error) {
        return serviceRouteError(error, 'Không thể tìm kiếm');
    }
}
