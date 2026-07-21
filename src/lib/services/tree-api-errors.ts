import { NextResponse } from 'next/server';
import { ZodError } from 'zod';
import { AuthenticationError } from '@/lib/auth/guards';
import { AuthorizationError } from '@/lib/auth/rbac';
import { BlobStorageError } from '@/lib/blob/client';
import { CompositeFeatureError } from '@/lib/composite/feature-flags';
import { CompositeConfigError } from './composite-config-service';
import { TreeServiceError } from './tree-service';

export function treeRouteError(error: unknown): NextResponse {
  if (error instanceof AuthenticationError) {
    return NextResponse.json(
      { ok: false, error: { code: 'UNAUTHENTICATED', message: error.message } },
      { status: 401 }
    );
  }
  if (error instanceof AuthorizationError) {
    const status =
      error.code === 'TREE_NOT_FOUND'
        ? 404
        : error.code === 'SOURCE_NOT_STANDALONE' ||
          error.code === 'SOURCE_FORBIDDEN' ||
          error.code === 'SOURCE_SHARING_NOT_CONSENTED' ||
          error.code === 'NOT_COMPOSITE_TREE'
          ? 422
          : 403;
    return NextResponse.json(
      { ok: false, error: { code: error.code, message: error.message } },
      { status }
    );
  }
  if (error instanceof CompositeFeatureError) {
    return NextResponse.json(
      { ok: false, error: { code: error.code, message: error.message, details: { feature: error.feature } } },
      { status: 404 }
    );
  }
  if (error instanceof CompositeConfigError) {
    const status = compositeConfigErrorStatus(error.code);
    return NextResponse.json(
      { ok: false, error: { code: error.code, message: error.message, ...(error.details ? { details: error.details } : {}) } },
      { status }
    );
  }
  if (error instanceof ZodError) {
    return NextResponse.json(
      {
        ok: false,
        error: {
          code: 'VALIDATION_ERROR',
          message: 'Dữ liệu cây gia phả không hợp lệ',
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
  if (error instanceof TreeServiceError) {
    return NextResponse.json(
      { ok: false, error: { code: error.code, message: error.message } },
      { status: error.code === 'NOT_FOUND' ? 404 : 400 }
    );
  }
  if (error instanceof BlobStorageError) {
    const status = error.code === 'NOT_FOUND' ? 404 : error.code === 'FORBIDDEN' ? 403 : 503;
    return NextResponse.json(
      { ok: false, error: { code: error.code, message: error.message } },
      { status }
    );
  }

  console.error('[trees] request failed', error);
  return NextResponse.json(
    { ok: false, error: { code: 'INTERNAL_ERROR', message: 'Không thể xử lý cây gia phả' } },
    { status: 500 }
  );
}

function compositeConfigErrorStatus(code: CompositeConfigError['code']): number {
  switch (code) {
    case 'NOT_FOUND':
      return 404;
    case 'STALE_CONFIG_REVISION':
      return 409;
    case 'COMPOSITE_READ_ONLY':
    case 'NOT_COMPOSITE_TREE':
    case 'SOURCE_NOT_STANDALONE':
    case 'DUPLICATE_RELATIONSHIP':
    case 'RELATIONSHIP_CYCLE':
    case 'IDENTITY_REFERENCE_CONFLICT':
    case 'INVALID_COMPOSITE_CONFIG':
    case 'SOURCE_LIMIT_EXCEEDED':
    case 'COMPOSITE_NOT_PUBLISHED':
      return 422;
    case 'SOURCE_FORBIDDEN':
      return 403;
    case 'SOURCE_UNAVAILABLE':
      return 503;
    case 'INVALID_INPUT':
    case 'INVALID_SCOPE':
    case 'REFERENCE_OUT_OF_SCOPE':
      return 400;
    default:
      return 400;
  }
}
