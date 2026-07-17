import { getToken } from 'next-auth/jwt';
import { NextRequest, NextResponse } from 'next/server';
import { AUTH_SECRET } from '@/lib/auth/constants';

const PUBLIC_PATHS = [
  '/api/auth',
  '/vi/login',
  '/vi/register',
  '/en/login',
  '/en/register'
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

  if (process.env.NODE_ENV === 'production') {
    response.headers.set('Strict-Transport-Security', 'max-age=63072000; includeSubDomains; preload');
  }
  response.headers.set('X-Content-Type-Options', 'nosniff');
  response.headers.set('Referrer-Policy', 'strict-origin-when-cross-origin');
  return response;
}

export const config = {
  matcher: ['/((?!_next/static|_next/image|favicon.ico|manifest.json|sw.js).*)']
};
