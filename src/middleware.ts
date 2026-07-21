import { getToken } from 'next-auth/jwt';
import createIntlMiddleware from 'next-intl/middleware';
import { NextRequest, NextResponse } from 'next/server';
import { AUTH_SECRET } from '@/lib/auth/constants';
import { ShareTokenError, verifySignedShareToken } from '@/lib/auth/share-token';
import { isSupportedLocale } from '@/i18n/config';
import { routing } from '@/i18n/routing';

const PUBLIC_PATHS = [
  '/api/auth',
  '/api/cron/backups',
  '/share/unavailable'
];
const handleIntlRouting = createIntlMiddleware(routing);

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
      setCacheHeaders(request, shareResponse);
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

  if (pathname === '/share/unavailable' || pathname.startsWith('/share/unavailable/')) {
    const response = NextResponse.next();
    setSecurityHeaders(response);
    return response;
  }

  const isApiPath = pathname === '/api' || pathname.startsWith('/api/');
  const locale = getPathLocale(pathname);

  // Locale negotiation happens before authentication so bare URLs are routed
  // consistently and the subsequent auth redirect can preserve that locale.
  if (!isApiPath && !locale) {
    const response = handleIntlRouting(request);
    setSecurityHeaders(response);
    return response;
  }

  const isPublic = PUBLIC_PATHS.some(
    (publicPath) => pathname === publicPath || pathname.startsWith(`${publicPath}/`)
  ) || isLocalizedAuthPath(pathname);

  let response: NextResponse;
  if (isPublic) {
    response = isApiPath ? NextResponse.next() : handleIntlRouting(request);
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

      const loginUrl = new URL(`/${locale ?? routing.defaultLocale}/login`, request.url);
      loginUrl.searchParams.set('callbackUrl', `${pathname}${request.nextUrl.search}`);
      return NextResponse.redirect(loginUrl);
    }

    response = isApiPath ? NextResponse.next() : handleIntlRouting(request);
  }

  setSecurityHeaders(response);
  setCacheHeaders(request, response);
  return response;
}

function getPathLocale(pathname: string): 'vi' | 'en' | null {
  const segment = pathname.split('/')[1];
  return isSupportedLocale(segment) ? segment : null;
}

function isLocalizedAuthPath(pathname: string): boolean {
  return /^\/(?:vi|en)\/(?:login|register)\/?$/.test(pathname);
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

export function setCacheHeaders(request: NextRequest, response: NextResponse): void {
  const pathname = request.nextUrl.pathname;
  const isApi = pathname === '/api' || pathname.startsWith('/api/');

  if (isApi) {
    const cacheControl = request.method === 'GET' && pathname.endsWith('/content')
      ? 'private, max-age=3600'
      : 'private, no-store';
    response.headers.set('Cache-Control', cacheControl);
    appendVary(response.headers, 'Cookie');
    appendVary(response.headers, 'Authorization');
    return;
  }

  // Authenticated HTML/RSC must be revalidated per user. Next's immutable
  // static chunks and optimized images are excluded by the middleware matcher.
  response.headers.set('Cache-Control', 'private, no-cache, must-revalidate');
  appendVary(response.headers, 'Cookie');
}

function appendVary(headers: Headers, value: string): void {
  const current = headers.get('Vary');
  const values = new Set((current ?? '').split(',').map((item) => item.trim()).filter(Boolean));
  values.add(value);
  headers.set('Vary', [...values].join(', '));
}

export const config = {
  matcher: ['/((?!_next/static|_next/image|favicon.ico|manifest.json|sw.js).*)']
};
