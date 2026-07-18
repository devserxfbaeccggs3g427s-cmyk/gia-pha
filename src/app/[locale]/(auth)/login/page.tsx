import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';
import { LoginForm } from '@/components/auth/login-form';
import { isSupportedLocale } from '@/i18n/config';

interface LoginPageProps {
  params: { locale: string };
  searchParams: { callbackUrl?: string; verified?: string; error?: string };
}

export async function generateMetadata({ params }: LoginPageProps): Promise<Metadata> {
  if (!isSupportedLocale(params.locale)) return {};
  const t = await getTranslations({ locale: params.locale, namespace: 'auth.login' });
  return { title: t('metaTitle') };
}

export default function LoginPage({ params, searchParams }: LoginPageProps) {
  return (
    <LoginForm
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
