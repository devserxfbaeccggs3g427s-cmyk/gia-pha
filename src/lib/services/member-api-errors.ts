import { NextResponse } from 'next/server';
import { ZodError } from 'zod';
import { BlobStorageError } from '@/lib/blob/client';
import { AuthenticationError } from '@/lib/auth/guards';
import { MemberServiceError } from './member-service';
import { CompositeConfigError } from './composite-config-service';

export function memberRouteError(error: unknown): NextResponse {
  if (error instanceof AuthenticationError) {
    return NextResponse.json(
      { ok: false, error: { code: 'UNAUTHENTICATED', message: error.message } },
      { status: 401 }
    );
  }
  if (error instanceof ZodError) {
    return NextResponse.json(
      {
        ok: false,
        error: {
          code: 'VALIDATION_ERROR',
          message: 'Dữ liệu thành viên không hợp lệ',
          details: error.flatten()
        }
      },
      { status: 400 }
    );
  }
  if (error instanceof SyntaxError) {
    return NextResponse.json(
      { ok: false, error: { code: 'VALIDATION_ERROR', message: 'Request body phải là JSON hợp lệ' } },
      { status: 400 }
    );
  }
  if (error instanceof CompositeConfigError && error.code === 'COMPOSITE_READ_ONLY') {
    return NextResponse.json(
      { ok: false, error: { code: error.code, message: 'Gia phả tổng hợp chỉ hiển thị dữ liệu từ các cây nguồn. Hãy mở cây nguồn phù hợp để thêm thành viên mới; thành viên sẽ tự động xuất hiện trong gia phả tổng hợp theo phạm vi đã cấu hình.' } },
      { status: 422 }
    );
  }
  if (error instanceof MemberServiceError) {
    const status = error.code === 'NOT_FOUND' ? 404 : error.code === 'CONFLICT' ? 409 : 400;
    return NextResponse.json({ ok: false, error: { code: error.code, message: error.message } }, { status });
  }
  if (error instanceof BlobStorageError) {
    const status = error.code === 'NOT_FOUND' ? 404 : error.code === 'FORBIDDEN' ? 403 : 503;
    return NextResponse.json({ ok: false, error: { code: error.code, message: error.message } }, { status });
  }
  if (error instanceof Error && 'code' in error && (error as { code?: string }).code === 'TREE_NOT_FOUND') {
    return NextResponse.json({ ok: false, error: { code: 'TREE_NOT_FOUND', message: error.message } }, { status: 404 });
  }
  if (error instanceof Error && 'code' in error && (error as { code?: string }).code === 'FORBIDDEN') {
    return NextResponse.json({ ok: false, error: { code: 'FORBIDDEN', message: error.message } }, { status: 403 });
  }
  console.error('[members] request failed', error);
  return NextResponse.json(
    { ok: false, error: { code: 'INTERNAL_ERROR', message: 'Không thể xử lý thành viên' } },
    { status: 500 }
  );
}
