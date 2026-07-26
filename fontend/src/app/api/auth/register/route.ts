import { NextResponse } from 'next/server';
import { ZodError } from 'zod';
import { registerSchema } from '@/data/schemas';
import { AuthServiceError } from '@/lib/auth/errors';
import { serviceRouteError } from '@/lib/api/service-error-handler';
import { springFetch } from '@/lib/api/spring-client';
import { BRIDGE_COOKIE_NAME } from '@/lib/auth/bridge-jwt';

export const runtime = 'nodejs';

/**
 * Registration endpoint. Delegates to {@code identity-service} via the
 * Spring gateway; the BFF does not implement password hashing or
 * user-store writes. Identity writes are strictly single-writer per
 * ADR-009.
 */
export async function POST(request: Request): Promise<NextResponse> {
    try {
        const input = registerSchema.parse(await request.json());
        const res = await springFetch<{
            userId: string;
            emailVerificationRequired: boolean;
        }>(
            '/api/auth/register',
            { method: 'POST', body: input, public: true },
            `${BRIDGE_COOKIE_NAME}=noop`
        );
        const message = res.body.emailVerificationRequired
            ? 'Tài khoản đã được tạo. Vui lòng kiểm tra email để xác nhận tài khoản.'
            : 'Tài khoản đã được tạo. Bạn có thể đăng nhập ngay.';

        return NextResponse.json(
            {
                ok: true,
                userId: res.body.userId,
                message,
                emailVerificationRequired: res.body.emailVerificationRequired
            },
            { status: 201 }
        );
    } catch (error) {
        if (error instanceof ZodError) {
            return NextResponse.json(
                { ok: false, error: { code: 'VALIDATION_ERROR', message: 'Dữ liệu đăng ký không hợp lệ', details: error.flatten() } },
                { status: 400 }
            );
        }
        if (error instanceof AuthServiceError) {
            const status = error.code === 'EMAIL_ALREADY_EXISTS' ? 409 : 400;
            return NextResponse.json(
                { ok: false, error: { code: error.code, message: error.message } },
                { status }
            );
        }
        return serviceRouteError(error, 'Không thể đăng ký');
    }
}
