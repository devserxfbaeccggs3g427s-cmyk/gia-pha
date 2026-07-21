import { NextResponse } from 'next/server';
import { ZodError } from 'zod';
import { AuthenticationError } from '@/lib/auth/guards';
import { AuthorizationError } from '@/lib/auth/rbac';
import { BlobStorageError } from '@/lib/blob/client';
import { EventServiceError, MediaServiceError } from './event-media-errors';
import { CompositeConfigError } from './composite-config-service';

export function eventMediaRouteError(error: unknown, resource: 'event' | 'media'): NextResponse {
  if (error instanceof AuthenticationError) {
    return failure(401, 'UNAUTHENTICATED', error.message);
  }
  if (error instanceof AuthorizationError) {
    return failure(error.code === 'TREE_NOT_FOUND' ? 404 : 403, error.code, error.message);
  }
  if (error instanceof CompositeConfigError) {
    return failure(error.code === 'COMPOSITE_READ_ONLY' ? 422 : 400, error.code, error.message);
  }
  if (error instanceof ZodError) {
    return NextResponse.json({
      ok: false,
      error: {
        code: 'VALIDATION_ERROR',
        message: resource === 'event' ? 'Dữ liệu sự kiện không hợp lệ' : 'Dữ liệu media không hợp lệ',
        details: error.flatten()
      }
    }, { status: 400 });
  }
  if (error instanceof SyntaxError) {
    return failure(400, 'VALIDATION_ERROR', 'Request body không hợp lệ');
  }
  if (error instanceof EventServiceError || error instanceof MediaServiceError) {
    const status = error.code === 'NOT_FOUND'
      ? 404
      : error.code === 'CONFLICT'
        ? 409
        : error.code === 'FILE_TOO_LARGE'
          ? 413
          : 400;
    return failure(status, error.code, error.message);
  }
  if (error instanceof BlobStorageError) {
    const status = error.code === 'NOT_FOUND'
      ? 404
      : error.code === 'FORBIDDEN'
        ? 403
        : error.code === 'CONFLICT'
          ? 409
          : 503;
    return failure(status, error.code, error.message);
  }
  console.error(`[${resource}] request failed`, error);
  return failure(
    500,
    'INTERNAL_ERROR',
    resource === 'event' ? 'Không thể xử lý sự kiện' : 'Không thể xử lý media'
  );
}

function failure(status: number, code: string, message: string): NextResponse {
  return NextResponse.json({ ok: false, error: { code, message } }, { status });
}
