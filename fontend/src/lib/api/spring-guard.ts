import 'server-only';

import { cookies } from 'next/headers';

import { ServiceError } from '@/lib/api/service-error';
import { BRIDGE_COOKIE_NAME } from '@/lib/auth/bridge-jwt';

/**
 * Reads the bridge cookie from the current Next.js request and returns
 * a {@code Cookie} header value ready for the gateway.
 *
 * <p>Throws {@link TreeServiceError} when no bridge cookie is present.
 * The bridge cookie is issued by {@code /api/auth/bridge} after a
 * successful NextAuth session validation (Task 18.2, monolith spec).
 */
export async function requireBridgeCookie(): Promise<string> {
    const store = await cookies();
    const token = store.get(BRIDGE_COOKIE_NAME)?.value;
    if (!token) {
        throw new ServiceError('UNAUTHORIZED', 'Authentication required');
    }
    return `${BRIDGE_COOKIE_NAME}=${encodeURIComponent(token)}`;
}
