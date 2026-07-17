import type { Metadata } from 'next';
import { LoginForm } from '@/components/auth/login-form';

export const metadata: Metadata = {
  title: 'Đăng nhập | Quản lý gia phả'
};

interface LoginPageProps {
  params: { locale: string };
  searchParams: { callbackUrl?: string; verified?: string; error?: string };
}

export default function LoginPage({ params, searchParams }: LoginPageProps) {
  return (
    <LoginForm
      locale={params.locale === 'en' ? 'en' : 'vi'}
      callbackUrl={searchParams.callbackUrl}
      emailVerified={searchParams.verified === '1'}
      verificationError={searchParams.error === 'INVALID_TOKEN'}
      oauth={{
        google: Boolean(process.env.GOOGLE_CLIENT_ID && process.env.GOOGLE_CLIENT_SECRET),
        facebook: Boolean(process.env.FACEBOOK_CLIENT_ID && process.env.FACEBOOK_CLIENT_SECRET)
      }}
    />
  );
}

