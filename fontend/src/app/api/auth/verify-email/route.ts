import { NextResponse } from 'next/server';
import { springFetch } from '@/lib/api/spring-client';
import { BRIDGE_COOKIE_NAME } from '@/lib/auth/bridge-jwt';

export const runtime = 'nodejs';

export async function GET(request: Request): Promise<NextResponse> {
    const url = new URL(request.url);
    const token = url.searchParams.get('token');
    const redirectUrl = new URL('/vi/login', url.origin);

    if (!token) {
        redirectUrl.searchParams.set('error', 'INVALID_TOKEN');
        return NextResponse.redirect(redirectUrl);
    }

    try {
        await springFetch(
            '/api/auth/verify-email',
            {
                method: 'POST',
                public: true,
                body: { token }
            },
            `${BRIDGE_COOKIE_NAME}=noop`
        );
        redirectUrl.searchParams.set('verified', '1');
    } catch {
        redirectUrl.searchParams.set('error', 'EXPIRED_OR_INVALID');
    }
    return NextResponse.redirect(redirectUrl);
}
