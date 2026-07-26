import { NextResponse } from 'next/server';
import { requireBridgeCookie } from '@/lib/api/spring-guard';
import { springFetch } from '@/lib/api/spring-client';
import { serviceRouteError } from '@/lib/api/service-error-handler';

export const runtime = 'nodejs';

/**
 * Public share-link resolution endpoint. The gateway forwards to
 * {@code sharing-service} which returns the allowlisted public projection
 * (no membership / owner / email / raw blob URL — per requirement 11.5).
 *
 * <p>The response sets {@code Cache-Control: no-store} and
 * {@code X-Robots-Tag: noindex} (Task 33.5).
 */
export async function GET(
  _request: Request,
  { params }: { params: Promise<{ token: string }> }
): Promise<NextResponse> {
  try {
    const { token } = await params;
    const cookie = await requireBridgeCookie();
    const res = await springFetch<{
      treeId: string;
      publicView: unknown;
    }>(
      `/api/public/share/${encodeURIComponent(token)}`,
      { method: 'GET', public: true },
      cookie
    );
    return NextResponse.json(res.body, {
      headers: {
        'Cache-Control': 'no-store',
        'X-Robots-Tag': 'noindex'
      }
    });
  } catch (error) {
    return serviceRouteError(error, 'Không thể truy xuất share link');
  }
}
