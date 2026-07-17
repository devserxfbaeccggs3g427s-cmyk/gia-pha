import { NextResponse } from 'next/server';
import { ZodError } from 'zod';
import { registerSchema } from '@/data/schemas';
import { registerUser } from '@/lib/auth/auth-service';
import { AuthServiceError } from '@/lib/auth/errors';

export const runtime = 'nodejs';

export async function POST(request: Request): Promise<NextResponse> {
  try {
    const input = registerSchema.parse(await request.json());
    const baseUrl = process.env.NEXTAUTH_URL ?? new URL(request.url).origin;
    const registration = await registerUser(input, baseUrl);
    const message = registration.emailVerificationRequired
      ? 'Tài khoản đã được tạo. Vui lòng kiểm tra email để xác nhận tài khoản.'
      : 'Tài khoản đã được tạo. Bạn có thể đăng nhập ngay.';

    return NextResponse.json(
      {
        ok: true,
        data: {
          user: registration.user,
          emailVerificationRequired: registration.emailVerificationRequired,
          message
        }
      },
      { status: 201 }
    );
  } catch (error) {
    if (error instanceof ZodError) {
      return NextResponse.json(
        {
          ok: false,
          error: {
            code: 'VALIDATION_ERROR',
            message: 'Dữ liệu đăng ký không hợp lệ',
            details: error.flatten()
          }
        },
        { status: 400 }
      );
    }

    if (error instanceof AuthServiceError) {
      const status = error.code === 'EMAIL_ALREADY_EXISTS' ? 409 : 503;
      return NextResponse.json(
        { ok: false, error: { code: error.code, message: error.message } },
        { status }
      );
    }

    console.error('[auth] Registration failed', error);
    return NextResponse.json(
      { ok: false, error: { code: 'INTERNAL_ERROR', message: 'Không thể tạo tài khoản' } },
      { status: 500 }
    );
  }
}
