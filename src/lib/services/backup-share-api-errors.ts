import { NextResponse } from 'next/server';
import { ZodError } from 'zod';
import { AuthenticationError } from '@/lib/auth/guards';
import { AuthorizationError } from '@/lib/auth/rbac';
import { BlobStorageError } from '@/lib/blob/client';
import { BackupServiceError } from './backup-service';
import { ShareLinkServiceError } from './share-link-service';

export function backupShareRouteError(error: unknown): NextResponse {
  if (error instanceof AuthenticationError) return failure(401, 'UNAUTHENTICATED', error.message);
  if (error instanceof AuthorizationError) {
    return failure(error.code === 'TREE_NOT_FOUND' ? 404 : 403, error.code, error.message);
  }
  if (error instanceof ZodError) return failure(400, 'VALIDATION_ERROR', 'Request data is invalid', error.flatten());
  if (error instanceof SyntaxError) return failure(400, 'INVALID_INPUT', 'Request body is not valid JSON');
  if (error instanceof BackupServiceError) {
    const status = error.code === 'TREE_NOT_FOUND' || error.code === 'BACKUP_NOT_FOUND'
      ? 404
      : error.code === 'BACKUP_EXPIRED'
        ? 410
        : error.code === 'RESTORE_FAILED'
          ? 503
          : 400;
    return failure(status, error.code, error.message);
  }
  if (error instanceof ShareLinkServiceError) {
    const status = error.code === 'TREE_NOT_FOUND' || error.code === 'LINK_NOT_FOUND'
      ? 404
      : error.code === 'LINK_EXPIRED'
        ? 410
        : 400;
    return failure(status, error.code, error.message);
  }
  if (error instanceof BlobStorageError) {
    const status = error.code === 'NOT_FOUND' ? 404 : error.code === 'FORBIDDEN' ? 403 : 503;
    return failure(status, error.code, error.message);
  }
  console.error('[backup-share] request failed', error);
  return failure(500, 'INTERNAL_ERROR', 'Could not process backup or sharing request');
}

function failure(status: number, code: string, message: string, details?: unknown): NextResponse {
  return NextResponse.json(
    { ok: false, error: { code, message, ...(details !== undefined ? { details } : {}) } },
    { status, headers: { 'Cache-Control': 'private, no-store' } }
  );
}
