import { NextResponse } from 'next/server';
import { ZodError } from 'zod';
import { AuthenticationError } from '@/lib/auth/guards';
import { AuthorizationError } from '@/lib/auth/rbac';
import { BlobStorageError } from '@/lib/blob/client';
import { ReportServiceError } from './report-service';

export function reportRouteError(error: unknown): NextResponse {
  if (error instanceof AuthenticationError) return failure('UNAUTHENTICATED', error.message, 401);
  if (error instanceof AuthorizationError) {
    return failure(error.code, error.message, error.code === 'TREE_NOT_FOUND' ? 404 : 403);
  }
  if (error instanceof ZodError) {
    return NextResponse.json({
      ok: false,
      error: { code: 'VALIDATION_ERROR', message: 'Tham số báo cáo không hợp lệ', details: error.flatten() }
    }, { status: 400 });
  }
  if (error instanceof ReportServiceError) {
    const status = error.code === 'MEMBER_NOT_FOUND' || error.code === 'TREE_NOT_FOUND'
      ? 404
      : error.code === 'INVALID_INPUT' ? 400 : 500;
    return failure(error.code, error.message, status);
  }
  if (error instanceof BlobStorageError) {
    const status = error.code === 'NOT_FOUND' ? 404 : error.code === 'FORBIDDEN' ? 403 : 503;
    return failure(error.code, error.message, status);
  }
  console.error('[reports] request failed', error);
  return failure('INTERNAL_ERROR', 'Không thể tạo báo cáo thống kê', 500);
}

function failure(code: string, message: string, status: number): NextResponse {
  return NextResponse.json({ ok: false, error: { code, message } }, { status });
}

