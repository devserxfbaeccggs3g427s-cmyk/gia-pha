import { getToken } from 'next-auth/jwt';
import { NextRequest, NextResponse } from 'next/server';
import { AUTH_SECRET } from '@/lib/auth/constants';
import { ShareTokenError, verifySignedShareToken } from '@/lib/auth/share-token';

const PUBLIC_PATHS = [
  '/api/auth',
  '/vi/login',
  '/vi/register',
  '/en/login',
  '/en/register',
  '/api/cron/backups',
  '/share/unavailable'
];

export async function middleware(request: NextRequest): Promise<NextResponse> {
  const forwardedProto = request.headers.get('x-forwarded-proto')?.split(',')[0].trim();
  const requestProtocol = forwardedProto ?? request.nextUrl.protocol.replace(':', '');

  if (process.env.NODE_ENV === 'production' && requestProtocol !== 'https') {
    const secureUrl = request.nextUrl.clone();
    secureUrl.protocol = 'https:';
    return NextResponse.redirect(secureUrl, 308);
  }

  const pathname = request.nextUrl.pathname;
  const shareToken = getShareToken(pathname);
  if (shareToken) {
    try {
      const link = await verifySignedShareToken(shareToken, AUTH_SECRET);
      const headers = new Headers(request.headers);
      headers.set('x-share-tree-id', link.treeId);
      headers.set('x-share-permission', 'VIEW');
      const shareResponse = NextResponse.next({ request: { headers } });
      setSecurityHeaders(shareResponse);
      shareResponse.headers.set('X-Robots-Tag', 'noindex, nofollow, noarchive');
      return shareResponse;
    } catch (error) {
      if (pathname.startsWith('/api/share/')) {
        const expired = error instanceof ShareTokenError && error.code === 'EXPIRED';
        return NextResponse.json(
          { ok: false, error: { code: expired ? 'LINK_EXPIRED' : 'LINK_NOT_FOUND', message: expired ? 'Share link has expired' : 'Share link not found' } },
          { status: expired ? 410 : 404, headers: { 'Cache-Control': 'private, no-store' } }
        );
      }
      const unavailableUrl = request.nextUrl.clone();
      unavailableUrl.pathname = '/share/unavailable';
      unavailableUrl.search = error instanceof ShareTokenError && error.code === 'EXPIRED' ? '?reason=expired' : '';
      return NextResponse.redirect(unavailableUrl);
    }
  }
  const isPublic = PUBLIC_PATHS.some(
    (publicPath) => pathname === publicPath || pathname.startsWith(`${publicPath}/`)
  );

  let response: NextResponse;
  if (isPublic) {
    response = NextResponse.next();
  } else {
    if (!AUTH_SECRET) {
      return NextResponse.json(
        { ok: false, error: { code: 'AUTH_CONFIGURATION_ERROR', message: 'Authentication is not configured' } },
        { status: 500 }
      );
    }

    const token = await getToken({ req: request, secret: AUTH_SECRET });

    if (!token) {
      if (pathname.startsWith('/api/')) {
        return NextResponse.json(
          { ok: false, error: { code: 'UNAUTHENTICATED', message: 'Authentication is required' } },
          { status: 401 }
        );
      }

      const locale = pathname.startsWith('/en') ? 'en' : 'vi';
      const loginUrl = new URL(`/${locale}/login`, request.url);
      loginUrl.searchParams.set('callbackUrl', `${pathname}${request.nextUrl.search}`);
      return NextResponse.redirect(loginUrl);
    }

    response = NextResponse.next();
  }

  setSecurityHeaders(response);
  return response;
}

function getShareToken(pathname: string): string | null {
  if (pathname === '/share/unavailable' || pathname.startsWith('/share/unavailable/')) return null;
  const match = /^\/(?:api\/)?share\/([^/]{1,512})\/?$/.exec(pathname);
  return match?.[1] ?? null;
}

function setSecurityHeaders(response: NextResponse): void {
  if (process.env.NODE_ENV === 'production') {
    response.headers.set('Strict-Transport-Security', 'max-age=63072000; includeSubDomains; preload');
  }
  response.headers.set('X-Content-Type-Options', 'nosniff');
  response.headers.set('Referrer-Policy', 'strict-origin-when-cross-origin');
}

export const config = {
  matcher: ['/((?!_next/static|_next/image|favicon.ico|manifest.json|sw.js).*)']
};
