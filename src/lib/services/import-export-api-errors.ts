import { NextResponse } from 'next/server';
import { ZodError } from 'zod';
import { AuthenticationError } from '@/lib/auth/guards';
import { AuthorizationError } from '@/lib/auth/rbac';
import { BlobStorageError } from '@/lib/blob/client';
import { ExportServiceError } from './export-service';
import { ImportServiceError } from './import-service';

export function importExportRouteError(error: unknown): NextResponse {
  if (error instanceof AuthenticationError) return failure(401, 'UNAUTHENTICATED', error.message);
  if (error instanceof AuthorizationError) return failure(error.code === 'TREE_NOT_FOUND' ? 404 : 403, error.code, error.message);
  if (error instanceof ZodError) return failure(400, 'VALIDATION_ERROR', 'Request data is invalid', error.flatten());
  if (error instanceof SyntaxError) return failure(400, 'INVALID_INPUT', 'Request body is not valid JSON');
  if (error instanceof Error && /required|must include|must be GEDCOM|format must/i.test(error.message)) {
    return failure(400, 'INVALID_INPUT', error.message);
  }
  if (error instanceof ImportServiceError) {
    const status = error.code === 'TREE_NOT_FOUND' ? 404 : error.code === 'WRITE_FAILED' ? 503 : error.code === 'INVALID_INPUT' ? 400 : 422;
    return failure(status, error.code, error.message, error.issues.length ? { issues: error.issues } : undefined);
  }
  if (error instanceof ExportServiceError) {
    const status = error.code === 'NOT_FOUND' ? 404 : error.code === 'INVALID_INPUT' ? 400 : error.code === 'EXPORT_TOO_LARGE' ? 413 : 500;
    return failure(status, error.code, error.message);
  }
  if (error instanceof BlobStorageError) {
    const status = error.code === 'NOT_FOUND' ? 404 : error.code === 'FORBIDDEN' ? 403 : 503;
    return failure(status, error.code, error.message);
  }
  console.error('[import-export] request failed', error);
  return failure(500, 'INTERNAL_ERROR', 'Could not process import/export request');
}

function failure(status: number, code: string, message: string, details?: unknown): NextResponse {
  return NextResponse.json({ ok: false, error: { code, message, ...(details ? { details } : {}) } }, { status });
}
