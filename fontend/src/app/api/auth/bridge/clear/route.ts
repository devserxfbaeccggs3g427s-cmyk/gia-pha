import { NextResponse } from 'next/server';

import { clearBridgeCookie } from '@/lib/auth/bridge-jwt';

export const runtime = 'nodejs';

/**
 * Clears the bridge cookie on logout / session loss (Task 18, Req 2.6). The
 * Spring session/cookie clear is handled by the final-auth endpoint installed
 * in Task 19; this endpoint only removes the bridge artifact so a stale token
 * cannot be replayed after the NextAuth session is gone.
 */
export async function POST(): Promise<Response> {
  const cookie = clearBridgeCookie({ secure: process.env.NODE_ENV === 'production' });
  return new Response(null, {
    status: 204,
    headers: {
      'Cache-Control': 'no-store',
      'Set-Cookie': cookie
    }
  });
}
