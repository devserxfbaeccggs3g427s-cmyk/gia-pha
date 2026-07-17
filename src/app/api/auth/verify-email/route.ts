import { NextResponse } from 'next/server';
import { verifyEmailToken } from '@/lib/auth/auth-service';
import { AuthServiceError } from '@/lib/auth/errors';

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
    await verifyEmailToken(token);
    redirectUrl.searchParams.set('verified', '1');
  } catch (error) {
    redirectUrl.searchParams.set(
      'error',
      error instanceof AuthServiceError ? error.code : 'INVALID_TOKEN'
    );
  }

  return NextResponse.redirect(redirectUrl);
}

