import 'server-only';

/**
 * HTTP client for the Spring Cloud Gateway (services/gateway-service:8080).
 *
 * <p>This client is the BFF adapter for the microservice-decomposition
 * research lane. Each Next.js API route calls a service in
 * {@code src/lib/services/*-service.ts}; that service method, in turn,
 * calls this client to reach the gateway. The gateway validates the
 * bridge-token JWT and forwards to the owning microservice.
 *
 * <p>Endpoints map 1:1 to the legacy Next.js API paths so the React
 * components don't change:
 *
 * <pre>
 *   GET    /api/trees                          -> GET    /identity-internal/trees (via gateway)
 *   POST   /api/trees                          -> POST   /identity-internal/trees
 *   GET    /api/trees/{treeId}                 -> GET    /tree/trees/{treeKey}
 *   GET    /api/trees/{treeId}/members         -> GET    /members/trees/{treeKey}/members
 *   ...
 * </pre>
 *
 * <p>Per .kiro/specs/microservice-decomposition/design.md §Security Model
 * the gateway forwards a short-lived user-context JWT
 * ({@code X-User-Context-Token}, ≤ 5 min) to downstream services. We
 * pass the bridge cookie on every call.
 */

const DEFAULT_TIMEOUT_MS = 10_000;

export interface SpringRequestOptions {
    method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
    body?: unknown;
    /** When true, send body as JSON. Default true for non-GET. */
    json?: boolean;
    /** Extra headers (e.g. Idempotency-Key). */
    headers?: Record<string, string>;
    /** Optional query parameters. */
    query?: Record<string, string | number | boolean | undefined>;
    /** Override the default request timeout (ms). */
    timeoutMs?: number;
    /** When true, do NOT include credentials (used for public endpoints). */
    public?: boolean;
}

export interface SpringResponse<T> {
    status: number;
    ok: boolean;
    body: T;
    headers: Headers;
}

export class SpringError extends Error {
    constructor(
        public readonly status: number,
        public readonly code: string,
        message: string,
        public readonly requestId?: string
    ) {
        super(message);
        this.name = 'SpringError';
    }
}

const GATEWAY_URL =
    process.env.GIAPHA_SPRING_BASE_URL ??
    process.env.NEXT_PUBLIC_GIAPHA_SPRING_BASE_URL ??
    'http://127.0.0.1:8080';

/**
 * Bridge cookie name. Set by {@code src/app/api/auth/bridge/route.ts}
 * after a successful NextAuth session validation. The actual constant
 * lives in {@code @/lib/auth/bridge-jwt} as {@code BRIDGE_COOKIE_NAME}.
 */
import { BRIDGE_COOKIE_NAME } from '@/lib/auth/bridge-jwt';
export { BRIDGE_COOKIE_NAME };

function gatewayUrl(path: string, query?: Record<string, string | number | boolean | undefined>): string {
    const normalizedPath = path.startsWith('/') ? path : `/${path}`;
    const url = new URL(normalizedPath, GATEWAY_URL);
    if (query) {
        for (const [k, v] of Object.entries(query)) {
            if (v === undefined || v === null) continue;
            url.searchParams.set(k, String(v));
        }
    }
    return url.toString();
}

async function readBody<T>(res: Response): Promise<T> {
    const text = await res.text();
    if (!text) return undefined as unknown as T;
    try {
        return JSON.parse(text) as T;
    } catch {
        return text as unknown as T;
    }
}

/**
 * Internal Spring call. Cookies from {@code cookieHeader} are forwarded
 * to the gateway; the gateway forwards them to identity-service for
 * session resolution, then mints a JWT for downstream services.
 */
export async function springFetch<T = unknown>(
    path: string,
    options: SpringRequestOptions = {},
    cookieHeader?: string
): Promise<SpringResponse<T>> {
    const method = options.method ?? (options.body !== undefined ? 'POST' : 'GET');
    const headers: Record<string, string> = {
        Accept: 'application/json',
        ...(options.headers ?? {}),
    };

    if (options.body !== undefined) {
        const useJson = options.json ?? method !== 'GET';
        if (useJson) {
            headers['Content-Type'] = 'application/json';
        }
    }

    if (cookieHeader && !options.public) {
        headers['Cookie'] = cookieHeader;
    }

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), options.timeoutMs ?? DEFAULT_TIMEOUT_MS);

    try {
        const res = await fetch(gatewayUrl(path, options.query), {
            method,
            headers,
            body:
                options.body === undefined
                    ? undefined
                    : headers['Content-Type'] === 'application/json'
                    ? JSON.stringify(options.body)
                    : (options.body as string),
            signal: controller.signal,
            cache: 'no-store',
        });

        const body = await readBody<T>(res);
        if (!res.ok) {
            const requestId = res.headers.get('X-Request-Id') ?? undefined;
            const code =
                (body && typeof body === 'object' && 'code' in body && typeof (body as { code: unknown }).code === 'string'
                    ? (body as { code: string }).code
                    : `HTTP_${res.status}`);
            const message =
                (body && typeof body === 'object' && 'message' in body && typeof (body as { message: unknown }).message === 'string'
                    ? (body as { message: string }).message
                    : res.statusText);
            throw new SpringError(res.status, code, message, requestId);
        }
        return { status: res.status, ok: res.ok, body, headers: res.headers };
    } finally {
        clearTimeout(timeout);
    }
}

/**
 * Convenience: extract the bridge cookie from a Next.js Request.
 * Returns null when the request is unauthenticated.
 */
export function readBridgeCookie(request: Request): string | null {
    const cookieHeader = request.headers.get('cookie');
    if (!cookieHeader) return null;
    const match = cookieHeader.match(/(?:^|;\s*)giapha\.bridge=([^;]+)/);
    return match ? decodeURIComponent(match[1]) : null;
}

/** Build a Cookie header value from a bridge token. */
export function bridgeCookieHeader(token: string): string {
    return `${BRIDGE_COOKIE_NAME}=${encodeURIComponent(token)}`;
}
