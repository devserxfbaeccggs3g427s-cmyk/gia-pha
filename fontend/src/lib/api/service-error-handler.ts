import { NextResponse } from 'next/server';
import { ZodError } from 'zod';

import { SpringError } from '@/lib/api/spring-client';
import { ServiceError } from '@/lib/api/service-error';
import { AuthenticationError } from '@/lib/auth/guards';
import { AuthorizationError } from '@/lib/auth/rbac';

/**
 * Map a service-layer exception to a Next.js Response with the
 * frozen error envelope used across the BFF.
 *
 * <p>The handler recognises: SpringError (microservice errors),
 * ServiceError (BFF-side validation), AuthenticationError,
 * AuthorizationError, ZodError, BlobStorageError.
 */
export function serviceRouteError(error: unknown, fallbackMessage = 'Yêu cầu không thành công'): NextResponse {
    if (error instanceof SpringError) {
        return NextResponse.json(
            { ok: false, error: { code: error.code, message: error.message } },
            { status: error.status }
        );
    }
    if (error instanceof ServiceError) {
        const status =
            error.code === 'NOT_FOUND' ? 404
                : error.code === 'UNAUTHORIZED' ? 401
                : error.code === 'FORBIDDEN' ? 403
                : error.code === 'CONFLICT' ? 409
                : 400;
        return NextResponse.json(
            { ok: false, error: { code: error.code, message: error.message } },
            { status }
        );
    }
    if (error instanceof AuthenticationError) {
        return NextResponse.json(
            { ok: false, error: { code: 'UNAUTHENTICATED', message: error.message } },
            { status: 401 }
        );
    }
    if (error instanceof AuthorizationError) {
        return NextResponse.json(
            { ok: false, error: { code: error.code, message: error.message } },
            { status: error.code === 'TREE_NOT_FOUND' ? 404 : 403 }
        );
    }
    if (error instanceof ZodError) {
        return NextResponse.json(
            {
                ok: false,
                error: {
                    code: 'VALIDATION_ERROR',
                    message: 'Dữ liệu không hợp lệ',
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

    console.error('[service] request failed', error);
    return NextResponse.json(
        { ok: false, error: { code: 'INTERNAL_ERROR', message: fallbackMessage } },
        { status: 500 }
    );
}
