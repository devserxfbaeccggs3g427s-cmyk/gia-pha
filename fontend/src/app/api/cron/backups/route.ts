import { NextResponse } from 'next/server';
import { serviceRouteError } from '@/lib/api/service-error-handler';
import { requireBridgeCookie } from '@/lib/api/spring-guard';
import { springFetch } from '@/lib/api/spring-client';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

/**
 * Vercel Cron endpoint. Authenticates with {@code CRON_SECRET} then
 * delegates to the gateway's daily-backup job. The microservice
 * implementation owns retention (30 days) and idempotency (Task 34.3).
 */
export async function GET(request: Request): Promise<NextResponse> {
    const configuredSecret = process.env.CRON_SECRET;
    const authorization = request.headers.get('authorization');
    if (!configuredSecret || authorization !== `Bearer ${configuredSecret}`) {
        return NextResponse.json(
            { ok: false, error: { code: 'UNAUTHORIZED', message: 'Invalid cron credentials' } },
            { status: 401 }
        );
    }

    try {
        const cookie = await requireBridgeCookie();
        const res = await springFetch<{ processed: number; results: unknown[] }>(
            '/api/transfer/cron/daily-backup',
            { method: 'POST' },
            cookie
        );
        return NextResponse.json(res.body, {
            headers: { 'Cache-Control': 'private, no-store' }
        });
    } catch (error) {
        return serviceRouteError(error, 'Không thể chạy cron backup');
    }
}
