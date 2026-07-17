import { NextResponse } from 'next/server';
import { ZodError } from 'zod';
import { BlobStorageError } from '@/lib/blob/client';
import { AuthenticationError } from '@/lib/auth/guards';
import { AuthorizationError } from '@/lib/auth/rbac';
import { RelationshipServiceError } from './relationship-service';

export function relationshipRouteError(error: unknown): NextResponse {
  if (error instanceof AuthenticationError) {
    return NextResponse.json({ ok: false, error: { code: 'UNAUTHENTICATED', message: error.message } }, { status: 401 });
  }
  if (error instanceof AuthorizationError) {
    return NextResponse.json({ ok: false, error: { code: error.code, message: error.message } }, { status: error.code === 'TREE_NOT_FOUND' ? 404 : 403 });
  }
  if (error instanceof ZodError) {
    return NextResponse.json({
      ok: false,
      error: { code: 'VALIDATION_ERROR', message: 'Dữ liệu mối quan hệ không hợp lệ', details: error.flatten() }
    }, { status: 400 });
  }
  if (error instanceof SyntaxError) {
    return NextResponse.json({ ok: false, error: { code: 'VALIDATION_ERROR', message: 'Request body phải là JSON hợp lệ' } }, { status: 400 });
  }
  if (error instanceof RelationshipServiceError) {
    return NextResponse.json({ ok: false, error: { code: error.code, message: error.message } }, {
      status: error.code === 'NOT_FOUND' ? 404 : error.code === 'CONFLICT' ? 409 : 400
    });
  }
  if (error instanceof BlobStorageError) {
    const status = error.code === 'NOT_FOUND' ? 404 : error.code === 'FORBIDDEN' ? 403 : 503;
    return NextResponse.json({ ok: false, error: { code: error.code, message: error.message } }, { status });
  }
  console.error('[relationships] request failed', error);
  return NextResponse.json({ ok: false, error: { code: 'INTERNAL_ERROR', message: 'Không thể xử lý mối quan hệ' } }, { status: 500 });
}
