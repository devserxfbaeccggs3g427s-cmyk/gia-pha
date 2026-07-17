import { NextResponse } from 'next/server';
import { ZodError } from 'zod';
import { AuthenticationError } from '@/lib/auth/guards';
import { AuthorizationError } from '@/lib/auth/rbac';
import { BlobStorageError } from '@/lib/blob/client';
import { SearchServiceError } from './search-service';

export function searchRouteError(error: unknown): NextResponse {
  if (error instanceof AuthenticationError) {
    return failure('UNAUTHENTICATED', error.message, 401);
  }
  if (error instanceof AuthorizationError) {
    return failure(error.code, error.message, error.code === 'TREE_NOT_FOUND' ? 404 : 403);
  }
  if (error instanceof ZodError) {
    return NextResponse.json(
      {
        ok: false,
        error: {
          code: 'VALIDATION_ERROR',
          message: 'Tham số tìm kiếm không hợp lệ',
          details: error.flatten()
        }
      },
      { status: 400 }
    );
  }
  if (error instanceof SearchServiceError) {
    return failure(error.code, error.message, 400);
  }
  if (error instanceof BlobStorageError) {
    const status = error.code === 'NOT_FOUND' ? 404 : error.code === 'FORBIDDEN' ? 403 : 503;
    return failure(error.code, error.message, status);
  }

  console.error('[search] request failed', error);
  return failure('INTERNAL_ERROR', 'Không thể thực hiện tìm kiếm', 500);
}

function failure(code: string, message: string, status: number): NextResponse {
  return NextResponse.json({ ok: false, error: { code, message } }, { status });
}
