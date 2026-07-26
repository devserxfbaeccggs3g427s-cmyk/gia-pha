import 'server-only';

import { cookies } from 'next/headers';
import { NextResponse } from 'next/server';
import { getServerSession } from 'next-auth';

import { authOptions } from '@/lib/auth/options';
import {
    BRIDGE_MAX_LIFETIME_SECONDS,
    BRIDGE_COOKIE_NAME,
    issueBridgeToken,
    setBridgeCookie,
    type BridgeAuthStrength,
} from '@/lib/auth/bridge-jwt';
import { springFetch } from '@/lib/api/spring-client';

export const runtime = 'nodejs';

/**
 * Server-side bridge exchange endpoint (Task 18, ADR-009).
 *
 * <p>Next.js (not Spring) validates the encrypted NextAuth session.
 * The resolved user id and step-up strength are then mapped to a
 * short-lived asymmetric JWT consumed by the Spring gateway on
 * subsequent requests. The token is delivered as an {@code HttpOnly},
 * {@code SameSite=Lax} cookie scoped to {@code /api} — never persisted
 * in {@code localStorage} (Req 2.6).
 *
 * <p>When the Spring gateway is reachable, we forward the resolved
 * user-id to it (via {@code /api/internal/resolve-session}) so the
 * gateway's session-validation cache can short-circuit the next
 * request. The cookie is issued locally.
 *
 * <p>Returns {@code 204 No Content} on success together with the
 * bridge {@code Set-Cookie} header. Returns {@code 401} when no
 * session exists or the step-up strength requested cannot be
 * satisfied.
 */
export async function POST(request: Request): Promise<Response> {
    const session = await getServerSession(authOptions);
    const userId = session?.user?.id;
    if (!userId) {
        return NextResponse.json({ error: 'UNAUTHENTICATED' }, { status: 401 });
    }
    const body = await safeJson(request);
    const strength = normalizeStrength(body?.strength);
    const scopes = Array.isArray(body?.scopes)
        ? body.scopes.filter((s: unknown) => typeof s === 'string')
        : [];

    // Forward to gateway in the background so the gateway's session
    // validation cache is warmed. The cookie is still issued locally
    // because Next.js (not Spring) holds the NextAuth session.
    void warmGatewaySession(userId);

    const issued = issueBridgeToken({ sub: userId, scopes, strength });
    const cookie = setBridgeCookie(issued, {
        secure: process.env.NODE_ENV === 'production'
    });

    return new Response(null, {
        status: 204,
        headers: {
            'Cache-Control': 'no-store',
            'Set-Cookie': cookie,
            'X-Bridge-Expires-At': String(issued.expiresAt),
            'X-Bridge-Max-Lifetime': String(BRIDGE_MAX_LIFETIME_SECONDS),
            'X-Bridge-Token-Name': BRIDGE_COOKIE_NAME
        }
    });
}

function normalizeStrength(value: unknown): BridgeAuthStrength {
    if (value === 'REAUTHENTICATED' || value === 'HARDWARE') return value;
    return 'SESSION';
}

async function safeJson(request: Request): Promise<Record<string, unknown> | null> {
    try {
        const text = await request.text();
        if (!text) return null;
        return JSON.parse(text) as Record<string, unknown>;
    } catch {
        return null;
    }
}

export async function warmGatewaySession(userKey: string): Promise<void> {
    try {
        const sessionCookie = (await cookies())
            .get('next-auth.session-token')?.value;
        if (!sessionCookie) return;
        await springFetch(
            '/api/internal/identity/resolve-session',
            {
                method: 'GET',
                public: true,
                query: { sessionId: sessionCookie, userKey }
            },
            `${BRIDGE_COOKIE_NAME}=noop`
        );
    } catch {
        // gateway may be unreachable in dev; swallow.
    }
}
